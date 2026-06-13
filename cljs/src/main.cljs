(ns main
  (:require [app-root-path]
            [cljs-node-io.core :refer [make-parents slurp spit]]
            [clojure.edn :refer [read-string]]
            [clojure.math.combinatorics :refer [cartesian-product]]
            [clojure.set :refer [union]]
            [clojure.string :as string :refer [split-lines trim]]
            [com.rpl.specter :refer [ATOM BEFORE-ELEM FIRST LAST MAP-VALS NONE setval setval* srange transform transform*]]
            [flatland.ordered.map :refer [ordered-map]]
            [fs :refer [existsSync]]
            [net :refer [createConnection]]
            [os :refer [homedir tmpdir]]
            [path :refer [join]]
            [promesa.core :as promesa :refer [all]]))

(defonce state
  (atom {:items (ordered-map)
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

(def render-mark
  (comp #(string/replace % "c" " ")
        name))

(defn render-item
  [item]
  (str "["
       (render-mark (val item))
       "] "
       (key item)))

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

(def actions
  (union #{:<C-r> :s :u} mark-actions))

(def modes
  #{"n" "v"})

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
                     action
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

(def parse-position
  (comp vec
        (partial map dec)
        drop-last
        rest))

(defn mark
  [action]
  (promesa/let [buffer (.-buffer (:nvim @state))
                cursor-position (call-function "getpos" ".")
                selection-position (call-function "getpos" "v")
                bounds (sort (map parse-position [cursor-position selection-position]))
                range* (transform LAST inc (map first bounds))
                lines (.getLines buffer (clj->js (zipmap [:start :end] range*)))
                snapshot (->> lines
                              js->clj
                              (map strip-prefix)
                              (select-keys (:items @state))
                              (remove (comp (partial = action)
                                            last))
                              (into {}))
                length (.-length buffer)
                window (.-window (:nvim @state))]
    (when-not (empty? snapshot)
      (transform ATOM
                 (comp (partial transform* :items #(merge % (setval MAP-VALS action snapshot)))
                       (partial setval* [:undos BEFORE-ELEM] {:snapshot snapshot
                                                              :cursor (parse-position cursor-position)}))
                 state)
      (render-buffer))
    (request "nvim_input" "<Esc>")
    (->> bounds
         last
         (transform FIRST (comp (partial min length)
                                (partial + 2)))
         clj->js
         (set! (.-cursor window)))))

(defn undo
  []
  (when-not (empty? (:undos @state))
    (promesa/let [window (.-window (:nvim @state))
                  cursor* (:cursor (first (:undos @state)))]
      (transform ATOM
                 (comp (partial transform* :items #(->> @state
                                                        :undos
                                                        first
                                                        :snapshot
                                                        (merge %)))
                       (partial setval* [:undos FIRST] NONE))
                 state)
      (render-buffer)
      (set! (.-cursor window) (clj->js cursor*)))))

(defn handle
  [key-name]
  (if ((keyword key-name) mark-actions) (mark (keyword key-name))
      (case (keyword key-name)
        :s (see)
        :u (undo)))
  nil)

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
  (register-command plugin "Sift" sift {:nargs 1
                                        :sync true})
  (register-command plugin "Handle" handle {:nargs 1
                                            :sync true})
  (write-manifest chrome-hosts-directory {:allowed_origins ["chrome-extension://aobaoadfgfpeggekafmdlmgdondfnpdo"]})
  (write-manifest firefox-hosts-directory {:allowed_extensions ["@sift"]}))
