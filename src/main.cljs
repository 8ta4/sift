(ns main
  (:require [cljs-node-io.core :refer [slurp spit]]
            [clojure.edn :refer [read-string]]
            [clojure.string :as string :refer [split-lines trim]]
            [fs :refer [existsSync]]
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

(defn load
  []
  (promesa/let [buffer (.-buffer (:nvim @state))
                path (.-name buffer)]
    (.setOption buffer "buftype" "acwrite")
    (.setLines buffer
               (clj->js (if (existsSync path)
                          (map render-item
                               (read-string (slurp path)))
                          []))
               (clj->js {:start 0 :end -1}))
    (.setOption buffer "modifiable" false)))

(defn get-references
  []
  (promesa/let [references (.lua (:nvim @state) "return require('sift').config.references")]
    (js->clj references :keywordize-keys true)))

(defn main
  [plugin]
  (reset! state {:nvim (.-nvim plugin)})
  (.registerAutocmd plugin "BufReadCmd" load (clj->js {:pattern "*.sift"
                                                       :sync true}))
  (register-command plugin "Sift" sift {:nargs 1}))
