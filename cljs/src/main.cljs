(ns main
  (:require [app-root-path]
            [cljs-node-io.core :refer [make-parents slurp spit]]
            [clojure.edn :refer [read-string]]
            [clojure.math.combinatorics :refer [cartesian-product]]
            [clojure.set :refer [difference union]]
            [clojure.string :refer [lower-case split-lines trim]]
            [com.rpl.specter :refer [ATOM BEFORE-ELEM FIRST LAST MAP-VALS NONE select-one setval setval* submap transform transform*]]
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
         :query ""
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
  (and (or (not ((:toggles @state) (val item)))
           ((:current-overrides @state) (key item)))
       (or (not (:regex @state))
           (.test (:regex @state) (key item)))))

(def format-items
  (comp clj->js
        (partial map render-item)
        (partial filter visible?)))

(defn render-full
  []
  (promesa/let [buffer (:list (:buffer @state))]
    (.setOption buffer "modifiable" true)
    (.setLines buffer
               (format-items (:items @state))
               (clj->js {:start 0 :end -1}))
    (.setOption buffer "modifiable" false)))

(defn open-filter-window
  [buffer enter]
  (promesa/let [window (.openWindow (:nvim @state)
                                    buffer
                                    enter
                                    (clj->js {:height 1
                                              :split "below"
                                              :style "minimal"}))]
    (.setOption window "winfixbuf" true)
; If winfixheight is not set, opening and closing other windows may alter the filter-window height.
    (.setOption window "winfixheight" true)
    window))

(defn load
  []
  (promesa/let [list-buffer (.-buffer (:nvim @state))
                list-window (.-window (:nvim @state))
                filter-buffer (.createBuffer (:nvim @state) false true)
                filter-window (open-filter-window filter-buffer false)
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
      (.setOption list-window "winfixbuf" true)
      (run! #(request "nvim_create_autocmd" % (clj->js {:buf (.-id filter-buffer)
                                                        :command (str "Handle " (name :change))}))
            #{"TextChanged" "TextChangedI"})
      (let [items (read-string {:readers {'ordered/map ordered-map}} (slurp path))]
        (transform ATOM
                   #(merge % {:buffer {:filter filter-buffer
                                       :list list-buffer}
                              :items items
                              :order (zipmap (keys items) (range))
                              :window {:filter (.-id filter-window)
                                       :list (.-id list-window)}})
                   state))
      (render-full))))

(defn save
  []
  (promesa/let [buffer (.-buffer (:nvim @state))
                path (.-name buffer)]
    (->> @state
         :items
         pr-str
         (spit path))
    (.setOption buffer "modified" false)))

(defn close-other
  [k]
  (promesa/let [windows (.-windows (:nvim @state))]
    (if (->> windows
             js->clj
             count
             (= 2))
      (.quit (:nvim @state))
      (request "nvim_win_close" (k (:window @state)) true))))

(defn show-error
  []
  (.errWriteLine (:nvim @state) "E37: No write since last change"))

(defn close*
  [id]
  (when ((-> @state
             :window
             vals
             set)
         id)
    (promesa/let [modified (-> @state
                               :buffer
                               :list
                               (.getOption "modified"))
                  command (call-function "histget" ":" -1)
                  block (->> command
                             trim
                             last
                             (not= \!)
                             (and modified))]
      (if block
        (promesa/do
          (if (->> @state
                   :window
                   :list
                   (= id))
            (promesa/let [window (.openWindow (:nvim @state)
                                              (:list (:buffer @state))
                                              true
                                              (clj->js {:split "above"}))]
              (request "nvim_win_set_height" (:filter (:window @state)) 1)
              (.setOption window "winfixbuf" true)
              (setval [ATOM :window :list] (.-id window) state))
            (promesa/let [window (-> @state
                                     :buffer
                                     :filter
                                     (open-filter-window true))]
              (setval [ATOM :window :filter] (.-id window) state)))
          (show-error))
        (close-other :filter)))))

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
                before (->> @state
                            :items
                            (select-one (submap (map strip-prefix (js->clj lines))))
                            (remove (comp (partial = action)
                                          last))
                            (into {}))
                length (.-length buffer)
                window (.-window (:nvim @state))]
    (when-not (empty? before)
      (mark* (merge {:after (setval MAP-VALS action before)
                     :before before
                     :cursor (first bounds)
                     :added-overrides (-> before
                                          keys
                                          set
                                          (difference (:previous-overrides @state)))
                     :removed-overrides (->> before
                                             keys
                                             set
                                             (union (:current-overrides @state))
                                             (difference (:previous-overrides @state)))}
                    (select-one (submap #{:toggles
                                          :regex
                                          :query})
                                @state)))
      (render-full))
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

(defn render-split
  []
  (promesa/let [cursor (request "nvim_win_get_cursor" (:list (:window @state)))
                lines (-> @state
                          :buffer
                          :list
                          (.getLines (clj->js {:start (dec (first cursor))
                                               :end (first cursor)})))]
    (if (-> lines
            js->clj
            first
            empty?)
      (render-full)
      (promesa/let [buffer (:list (:buffer @state))
                    index ((:order @state) (->> lines
                                                js->clj
                                                first
                                                strip-prefix))]
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

(defn toggle
  [action]
  (toggle* (lower-case-keyword action))
  (render-split))

(defn undo*
  [step]
  (transform ATOM
             (comp (partial setval* [:redos BEFORE-ELEM] step)
                   (partial setval* [:undos FIRST] NONE)
                   (partial assign :current-overrides :previous-overrides)
                   (partial transform* :previous-overrides #(difference (union % (:removed-overrides step)) (:added-overrides step)))
                   (partial setval* :regex (or (:regex step)
                                               NONE))
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
      (render-full)
      (->> cursor*
           (transform FIRST inc)
           clj->js
           (set! (.-cursor window))))))

(defn redo*
  [step]
  (transform ATOM
             (comp (partial setval* [:undos BEFORE-ELEM] step)
                   (partial setval* [:redos FIRST] NONE)
                   (partial setval* :regex (or (:regex step)
                                               NONE))
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
      (render-full)
      (->> cursor*
           (transform FIRST inc)
           clj->js
           (set! (.-cursor window))))))

(defn change
  []
  (promesa/let [lines (-> @state
                          :buffer
                          :filter
                          .getLines)
                query (first (js->clj lines))]
    (try (transform ATOM
                    (comp (partial setval* :query query)
                          (partial setval* :regex (if (empty? query)
                                                    NONE
                                                    (js/RegExp. query "i"))))
                    state)
         (catch :default _))
    (render-split)))

(defn handle*
  [action]
  (cond (action mark-actions) (mark action)
        (action toggle-actions) (toggle action)
        :else ((case action
                 :change change
                 :s see
                 :u undo
                 :r redo))))

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
