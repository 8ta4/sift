(ns main
  (:require [app-root-path]
            [cljs-node-io.core :refer [make-parents slurp spit]]
            [clojure.edn :refer [read-string]]
            [clojure.math.combinatorics :refer [cartesian-product]]
            [clojure.set :refer [difference subset? union]]
            [clojure.string :refer [lower-case split-lines trim]]
            [com.rpl.specter :refer [ATOM BEFORE-ELEM FIRST LAST MAP-VALS NONE setval setval* transform transform*]]
            [flatland.ordered.map :refer [ordered-map]]
            [fs :refer [existsSync]]
            [net :refer [createConnection]]
            [os :refer [homedir tmpdir]]
            [path :refer [join]]
            [promesa.core :as promesa :refer [all]]))

(defonce state
  (atom {:toggles #{}
         :current-overrides #{}
         :previous-overrides #{}
         :undos []
         :redos []}))

(defn call-function
  [function-name & args]
  (.then (.callFunction (:nvim @state) function-name (clj->js args))
         #(js->clj % :keywordize-keys true)))

(def parse-items
  (comp (partial into (ordered-map))
        (partial map (juxt identity (constantly :c)))
        (partial remove empty?)
        (partial map trim)
        split-lines))

(defn sift
  [target]
  (promesa/let [s (call-function "getreg" "+")]
    (->> s
         parse-items
         pr-str
         (spit (str target ".sift")))
    (.command (:nvim @state) (str "e " target ".sift"))))

(defn register-command
  [plugin command-name f options]
  (.registerCommand plugin
                    command-name
                    #(apply f (js->clj % :keywordize-keys true))
                    (clj->js options)))

(defn request
  [function & args]
  (.then (.request (:nvim @state) function (clj->js args))
         #(js->clj % :keywordize-keys true)))

(def mark-actions
  #{:a :c :d :x})

(def toggle-actions
  #{:A :C :D :X})

(def actions
  (union #{:r :s :u} mark-actions toggle-actions))

(def modes
  #{"n" "v"})

; The commented-out version below causes `render-buffer` to take 100 ms or more on lists with 100,000 items.
; (def render-mark
;   (comp #(string/replace % "c" " ")
;         name))
(defn render-mark
  [mark]
  (if (= :c mark)
    " "
    (name mark)))

(defn render-item
  [item]
  (str "["
       (render-mark (val item))
       "] "
       (key item)))

(defn visible?
  [item]
  (or (not ((:toggles @state) (val item)))
      ((:current-overrides @state) (key item))))

(def format-items
  (comp clj->js
        (partial map render-item)
        (partial filter visible?)))

(defn render-buffer
  []
  (promesa/let [buffer (.-buffer (:nvim @state))]
    (.setOption buffer "modifiable" true)
    (.setLines buffer
               (format-items (:items @state))
               (clj->js {:start 0 :end -1}))
    (.setOption buffer "modifiable" false)))

(defn load
  []
  (promesa/let [list-buffer (.-buffer (:nvim @state))
                list-window (.-window (:nvim @state))
                filter-buffer (.createBuffer (:nvim @state) false true)
                filter-window (.openWindow (:nvim @state)
                                           filter-buffer
                                           false
                                           (clj->js {:height 1
                                                     :split "below"
                                                     :style "minimal"}))
                path (.-name list-buffer)]
    (when (existsSync path)
      (run! (fn [[mode action]]
              (request "nvim_buf_set_keymap"
                       (.-id list-buffer)
                       (name mode)
                       (get {"r" "<C-r>"} action action)
                       (str "<Cmd>:"
                            "Handle "
                            action
                            "<CR>")
                       {:nowait true
                        :silent true}))
            (cartesian-product modes (map name actions)))
      (.setOption list-buffer "buftype" "acwrite")
      (run! #(.setOption % "winfixbuf" true) #{list-window filter-window})
      (let [items (read-string {:readers {'ordered/map ordered-map}} (slurp path))]
        (transform ATOM
                   #(merge % {:buffer list-buffer
                              :items items
                              :order (zipmap (keys items) (range))
                              :window {:filter (.-id filter-window)
                                       :list (.-id list-window)}})
                   state))
      (render-buffer))))

(defn save
  []
  (promesa/let [buffer (.-buffer (:nvim @state))
                path (.-name buffer)]
    (->> @state
         :items
         pr-str
         (spit path))
    (.setOption buffer "modified" false)))

(defn close*
  [id]
  (condp = id
    (:list (:window @state)) (promesa/let [modified (.getOption (:buffer @state) "modified")
                                           loaded (.-loaded (:buffer @state))
                                           windows (.-windows (:nvim @state))]
; https://github.com/neovim/neovim/blob/a1da5d1f141f58158ffc33aa2c84e790633b57c9/runtime/doc/editing.txt#L1159-L1160
; When closed with `:q!`, the buffer is unloaded. When closed with `:q`, the buffer remains loaded.
                               (if (and modified loaded)
                                 (promesa/let [window (.openWindow (:nvim @state) (:buffer @state) true (clj->js {:split "above"}))]
                                   (request "nvim_win_set_height" (:filter (:window @state)) 1)
                                   (setval [ATOM :window :list] (.-id window) state))
; Checking `(count (js->clj windows))` is nondeterministic.
; The list window being closed may or may not still be present in Neovim's window list.
                                 (if (->> @state
                                          :window
                                          vals
                                          set
                                          (subset? (->> windows
                                                        js->clj
                                                        (map #(.-id %))
                                                        set)))
                                   (.quit (:nvim @state))
                                   (request "nvim_win_close" (:filter (:window @state)) true))))

    nil)
  nil)

(def close
  (comp close*
        parse-long))

(defn get-references
  []
  (promesa/let [references (.lua (:nvim @state) "return require('sift').config.references")]
    (js->clj references :keywordize-keys true)))

(def socket-path
  (join (tmpdir) "sift.sock"))

(defn encode
  [message]
  (let [payload (-> message
                    clj->js
                    js/JSON.stringify
                    js/Buffer.from)
        header (js/Buffer.alloc 4)]
    (.writeUInt32LE header (.-length payload))
    (js/Buffer.concat (clj->js [header payload]))))

(defn strip-prefix
  [line]
  (subs line 4))

(defn see
  []
  (promesa/let [line (.getLine (:nvim @state))]
    (when-not (empty? line)
      (promesa/let [references (get-references)
                    socket (createConnection socket-path)]
        (.on socket "connect" (fn []
                                (.write socket (encode {:references references
                                                        :text (strip-prefix line)}))
                                (.end socket)))))))

(defn assign
  [apath k structure]
  (setval apath (k structure) structure))

(defn mark*
  [step]
  (transform ATOM
             (comp (partial setval* :redos [])
                   (partial setval* [:undos BEFORE-ELEM] step)
                   (partial assign :current-overrides :previous-overrides)
                   (partial transform* :previous-overrides #(difference (union % (:added-overrides step)) (:removed-overrides step)))
                   (partial transform* :items #(merge % (:after step))))
             state))

(defn mark
  [action]
  (promesa/let [buffer (.-buffer (:nvim @state))
                positions (all (map (partial call-function "getpos") ["." "v"]))
                bounds (sort (map (comp vec
                                        (partial map dec)
                                        drop-last
                                        rest)
                                  positions))
                lines (->> bounds
                           (map first)
                           (transform LAST inc)
                           (zipmap [:start :end])
                           clj->js
                           (.getLines buffer))
                before (->> lines
                            js->clj
                            (map strip-prefix)
                            (select-keys (:items @state))
                            (remove (comp (partial = action)
                                          last))
                            (into {}))
                length (.-length buffer)
                window (.-window (:nvim @state))]
    (when-not (empty? before)
      (mark* {:after (setval MAP-VALS action before)
              :before before
              :cursor (first bounds)
              :toggles (:toggles @state)
              :added-overrides (-> before
                                   keys
                                   set
                                   (difference (:previous-overrides @state)))
              :removed-overrides (->> before
                                      keys
                                      set
                                      (union (:current-overrides @state))
                                      (difference (:previous-overrides @state)))})
      (render-buffer))
    (request "nvim_input" "<Esc>")
    (->> bounds
         last
         (transform FIRST (comp (partial min length)
                                (partial + 2)))
         clj->js
         (set! (.-cursor window)))))

(defn toggle-member
  [x coll]
  ((if (coll x)
     disj
     conj)
   coll
   x))

(def lower-case-keyword
  (comp keyword
        lower-case
        name))

(defn toggle*
  [action]
  (transform ATOM
             (comp (partial setval* :current-overrides #{})
                   (partial transform* :toggles (partial toggle-member action)))
             state))

(defn toggle
  [action]
  (toggle* (lower-case-keyword action))
  (promesa/let [line (.getLine (:nvim @state))]
    (when-not (empty? line)
      (promesa/let [buffer (.-buffer (:nvim @state))
                    index ((:order @state) (strip-prefix line))]
        (.setOption buffer "modifiable" true)
        (.setLines buffer
                   (->> @state
                        :items
                        (drop index)
                        format-items)
                   (clj->js {:start index :end -1}))
        (.setLines buffer
                   (->> @state
                        :items
                        (take index)
                        format-items)
                   (clj->js {:start 0 :end index}))
        (.setOption buffer "modifiable" false)))))

(defn undo*
  [step]
  (transform ATOM
             (comp (partial setval* [:redos BEFORE-ELEM] step)
                   (partial setval* [:undos FIRST] NONE)
                   (partial assign :current-overrides :previous-overrides)
                   (partial transform* :previous-overrides #(difference (union % (:removed-overrides step)) (:added-overrides step)))
                   (partial setval* :toggles (:toggles step))
                   (partial transform* :items #(merge % (:before step))))
             state))

(defn undo
  []
  (when-not (empty? (:undos @state))
    (promesa/let [window (.-window (:nvim @state))
                  cursor* (-> @state
                              :undos
                              first
                              :cursor)]
      (-> @state
          :undos
          first
          undo*)
      (render-buffer)
      (->> cursor*
           (transform FIRST inc)
           clj->js
           (set! (.-cursor window))))))

(defn redo*
  [step]
  (transform ATOM
             (comp (partial setval* [:undos BEFORE-ELEM] step)
                   (partial setval* [:redos FIRST] NONE)
                   (partial assign :current-overrides :previous-overrides)
                   (partial transform* :previous-overrides #(difference (union % (:added-overrides step)) (:removed-overrides step)))
                   (partial setval* :toggles (:toggles step))
                   (partial transform* :items #(merge % (:after step))))
             state))

(defn redo
  []
  (when-not (empty? (:redos @state))
    (promesa/let [window (.-window (:nvim @state))
                  cursor* (-> @state
                              :redos
                              first
                              :cursor)]
      (-> @state
          :redos
          first
          redo*)
      (render-buffer)
      (->> cursor*
           (transform FIRST inc)
           clj->js
           (set! (.-cursor window))))))

(defn handle*
  [action]
  (cond (action mark-actions) (mark action)
        (action toggle-actions) (toggle action)
        :else (case action
                :s (see)
                :u (undo)
                :r (redo)))
  nil)

(def handle
  (comp handle*
        keyword))

(def chrome-hosts-directory
; https://developer.chrome.com/docs/extensions/develop/concepts/native-messaging#:~:text=~/Library/Application%20Support/Google/Chrome/NativeMessagingHosts/com.my_company.my_application.json
  (join (homedir) "Library/Application Support/Google/Chrome/NativeMessagingHosts"))

(def firefox-hosts-directory
; https://github.com/mdn/content/blob/d45b7a7d45dac4a0012c138aba7afedc0f9e570c/files/en-us/mozilla/add-ons/webextensions/native_manifests/index.md?plain=1#L412
  (join (homedir) "Library/Application Support/Mozilla/NativeMessagingHosts"))

(def host-filename
  "host")

(def host-path
  (join (.toString app-root-path) "hs/bin" host-filename))

(defn write-manifest
  [directory manifest]
  (make-parents (join directory host-filename))
  (->> {:description "Native messaging host for the sift Neovim plugin"
        :name host-filename
        :path host-path
        :type "stdio"}
       (merge manifest)
       clj->js
       js/JSON.stringify
       (spit (join directory (str host-filename ".json")))))

(defn main
  [plugin]
  (setval [ATOM :nvim] (.-nvim plugin) state)
  (.registerAutocmd plugin "BufReadCmd" load (clj->js {:pattern "*.sift"
                                                       :sync true}))
  (.registerAutocmd plugin "BufWriteCmd" save (clj->js {:pattern "*.sift"
                                                        :sync true}))
  (.registerAutocmd plugin "WinClosed" close (clj->js {:eval "expand('<amatch>')"
                                                       :pattern "*"
                                                       :sync true}))
  (register-command plugin "Sift" sift {:nargs 1
                                        :sync true})
  (register-command plugin "Handle" handle {:nargs 1
                                            :sync true})
  (write-manifest chrome-hosts-directory {:allowed_origins ["chrome-extension://aobaoadfgfpeggekafmdlmgdondfnpdo"]})
  (write-manifest firefox-hosts-directory {:allowed_extensions ["@sift"]}))
