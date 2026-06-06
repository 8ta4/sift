(ns main
  (:require [cljs-node-io.core :refer [slurp spit]]
            [clojure.edn :refer [read-string]]
            [clojure.string :as string :refer [split-lines trim]]
            [fs :refer [existsSync]]
            [net :refer [createConnection]]
            [os :refer [tmpdir]]
            [path :refer [join]]
            [promesa.core :as promesa]))

(defonce state
  (atom {}))

(defn call-function
  [function-name options]
  (.then (.callFunction (:nvim @state) function-name (clj->js options))
         #(js->clj % :keywordize-keys true)))

(defn make-item
  [s]
  {:mark :c
   :text s})

(def parse
  (comp (partial map make-item)
        distinct
        (partial remove empty?)
        (partial map trim)
        split-lines))

(defn sift
  [f]
  (promesa/let [s (call-function "getreg" ["+"])]
    (->> s
         parse
         pr-str
         (spit (str f ".sift")))
    (.command (:nvim @state) (str "e " f ".sift"))))

(defn render-item
  [item]
  (str "["
       (-> item
           :mark
           name
           (string/replace "c" " "))
       "] "
       (:text item)))

(defn register-command
  [plugin command-name handle options]
  (.registerCommand plugin command-name #(apply handle (js->clj % :keywordize-keys true)) (clj->js options)))

(defn request
  [function & args]
  (.then (.request (:nvim @state) function (clj->js args))
         #(js->clj % :keywordize-keys true)))

(defn load
  []
  (promesa/let [buffer (.-buffer (:nvim @state))
                path (.-name buffer)]
    (request "nvim_buf_set_keymap" (.-id buffer) "n" "s" ":Handle s<CR>" {:silent true})
    (.setOption buffer "buftype" "acwrite")
    (.setLines buffer
               (clj->js (if (existsSync path)
                          (->> path
                               slurp
                               read-string
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

(defn see
  []
  (promesa/let [references (get-references)
                line (.getLine (:nvim @state))
                socket (createConnection socket-path)]
    (.on socket "connect" (fn []
                            (.write socket (pr-str {:references references
                                                    :text (subs line 4)}))
                            (.end socket)))))

(defn handle
  [key-name]
  (if (= "s" key-name)
    (see)))

(defn main
  [plugin]
  (reset! state {:nvim (.-nvim plugin)})
  (.registerAutocmd plugin "BufReadCmd" load (clj->js {:pattern "*.sift"
                                                       :sync true}))
  (register-command plugin "Sift" sift {:nargs 1})
  (register-command plugin "Handle" handle {:nargs 1
                                            :range ""}))
