(ns main
  (:require [app-root-path]
            [cljs-node-io.core :refer [make-parents slurp spit]]
            [clojure.edn :refer [read-string]]
            [clojure.string :as string :refer [split-lines trim]]
            [com.rpl.specter :refer [AFTER-ELEM ATOM FIRST setval transform]]
            [flatland.ordered.map :refer [ordered-map]]
            [fs :refer [existsSync]]
            [net :refer [createConnection]]
            [os :refer [homedir tmpdir]]
            [path :refer [join]]
            [promesa.core :as promesa]))

(defonce state
  (atom {}))

(defn call-function
  [function-name options]
  (.then (.callFunction (:nvim @state) function-name (clj->js options))
         #(js->clj % :keywordize-keys true)))

(def parse
  (comp (partial into (ordered-map))
        (partial map (juxt identity (constantly :c)))
        (partial remove empty?)
        (partial map trim)
        split-lines))

(defn sift
  [target]
  (promesa/let [s (call-function "getreg" ["+"])]
    (->> s
         parse
         pr-str
         (spit (str target ".sift")))
    (.command (:nvim @state) (str "e " target ".sift"))))

(defn render-item
  [item]
  (str "["
       (-> item
           val
           name
           (string/replace "c" " "))
       "] "
       (key item)))

(defn register-command
  [plugin command-name f options]
  (.registerCommand plugin command-name
                    (fn
                      ([args]
                       (apply f (js->clj args :keywordize-keys true)))
                      ([args range*]
                       (apply f (setval AFTER-ELEM
                                        (transform FIRST dec (js->clj range*))
                                        (js->clj args :keywordize-keys true)))))
                    (clj->js options)))

(defn request
  [function & args]
  (.then (.request (:nvim @state) function (clj->js args))
         #(js->clj % :keywordize-keys true)))

(defn load
  []
  (promesa/let [buffer (.-buffer (:nvim @state))
                path (.-name buffer)]
    (run! #(request "nvim_buf_set_keymap" (.-id buffer) % "s" "<Cmd>:Handle s<CR>" {:silent true})
          #{"n" "v"})
    (.setOption buffer "buftype" "acwrite")
    (.setLines buffer
               (clj->js (if (existsSync path)
                          (->> path
                               slurp
                               (read-string {:readers {'ordered/map ordered-map}})
                               (map render-item))
                          []))
               (clj->js {:start 0 :end -1}))
    (.setOption buffer "modifiable" false)))

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

(defn see
  []
  (promesa/let [references (get-references)
                line (.getLine (:nvim @state))
                socket (createConnection socket-path)]
    (.on socket "connect" (fn []
                            (.write socket (encode {:references references
                                                    :text (subs line 4)}))
                            (.end socket)))))

(defn handle
  [key-name range*]
  (if (= "s" key-name)
    (see)))

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
  (register-command plugin "Sift" sift {:nargs 1})
  (register-command plugin "Handle" handle {:nargs 1
                                            :range ""})
  (write-manifest chrome-hosts-directory {:allowed_origins ["chrome-extension://aobaoadfgfpeggekafmdlmgdondfnpdo"]})
  (write-manifest firefox-hosts-directory {:allowed_extensions ["@sift"]}))
