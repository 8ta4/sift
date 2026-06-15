(ns main
  (:require [app-root-path]
            [cljs-node-io.core :refer [make-parents slurp spit]]
            [clojure.edn :refer [read-string]]
            [clojure.math.combinatorics :refer [cartesian-product]]
            [clojure.set :refer [union]]
            [clojure.string :refer [lower-case split-lines trim]]
            [com.rpl.specter :refer [ATOM BEFORE-ELEM FIRST LAST MAP-VALS NONE setval setval* transform transform*]]
            [flatland.ordered.map :refer [ordered-map]]
            [fs :refer [existsSync]]
            [net :refer [createConnection]]
            [os :refer [homedir tmpdir]]
            [path :refer [join]]
            [promesa.core :as promesa :refer [all]]))

(defonce state
  (atom {:items (ordered-map)
         :toggles #{}
         :redos []
         :undos []}))

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

(defn render-buffer
  []
  (promesa/let [buffer (.-buffer (:nvim @state))]
    (.setOption buffer "modifiable" true)
    (.setLines buffer
               (clj->js (map render-item (:items @state)))
               (clj->js {:start 0 :end -1}))
    (.setOption buffer "modifiable" false)))

(defn load
  []
  (promesa/let [buffer (.-buffer (:nvim @state))
                path (.-name buffer)]
    (run! (fn [[mode action]]
            (request "nvim_buf_set_keymap"
                     (.-id buffer)
                     (name mode)
                     (get {"r" "<C-r>"} action action)
                     (str "<Cmd>:"
                          "Handle "
                          action
                          "<CR>")
                     {:nowait true
                      :silent true}))
          (cartesian-product modes (map name actions)))
    (.setOption buffer "buftype" "acwrite")
    (when (existsSync path)
      (setval [ATOM :items]
              (read-string {:readers {'ordered/map ordered-map}} (slurp path))
              state))
    (render-buffer)))

(defn save
  []
  (promesa/let [buffer (.-buffer (:nvim @state))
                path (.-name buffer)]
    (->> @state
         :items
         pr-str
         (spit path))
    (.setOption buffer "modified" false)))

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
  (promesa/let [references (get-references)
                line (.getLine (:nvim @state))
                socket (createConnection socket-path)]
    (.on socket "connect" (fn []
                            (.write socket (encode {:references references
                                                    :text (strip-prefix line)}))
                            (.end socket)))))

(defn mark*
  [step]
  (transform ATOM
             (comp (partial setval* :redos [])
                   (partial setval* [:undos BEFORE-ELEM] step)
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
      (mark* {:before before
              :after (setval MAP-VALS action before)
              :cursor (first bounds)})
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

(defn toggle
  [action]
  (transform ATOM
             (partial transform* :toggles (partial toggle-member (lower-case-keyword action)))
             state))

(defn undo*
  []
  (transform ATOM
             (comp (partial setval* [:redos BEFORE-ELEM] (first (:undos @state)))
                   (partial setval* [:undos FIRST] NONE)
                   (partial transform* :items #(->> @state
                                                    :undos
                                                    first
                                                    :before
                                                    (merge %))))
             state))

(defn undo
  []
  (when-not (empty? (:undos @state))
    (promesa/let [window (.-window (:nvim @state))
                  cursor* (:cursor (first (:undos @state)))]
      (undo*)
      (render-buffer)
      (set! (.-cursor window) (clj->js (transform FIRST inc cursor*))))))

(defn redo*
  []
  (transform ATOM
             (comp (partial setval* [:undos BEFORE-ELEM] (first (:redos @state)))
                   (partial setval* [:redos FIRST] NONE)
                   (partial transform* :items #(->> @state
                                                    :redos
                                                    first
                                                    :after
                                                    (merge %))))
             state))

(defn redo
  []
  (when-not (empty? (:redos @state))
    (promesa/let [window (.-window (:nvim @state))
                  cursor* (:cursor (first (:redos @state)))]
      (redo*)
      (render-buffer)
      (set! (.-cursor window) (clj->js (transform FIRST inc cursor*))))))

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
  (register-command plugin "Sift" sift {:nargs 1
                                        :sync true})
  (register-command plugin "Handle" handle {:nargs 1
                                            :sync true})
  (write-manifest chrome-hosts-directory {:allowed_origins ["chrome-extension://aobaoadfgfpeggekafmdlmgdondfnpdo"]})
  (write-manifest firefox-hosts-directory {:allowed_extensions ["@sift"]}))
