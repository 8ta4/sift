(ns main
  (:require [cljs-node-io.core :refer [slurp spit]]
            [clojure.edn :refer [read-string]]
            [clojure.string :refer [split-lines trim]]
            [fs :refer [existsSync]]
            [promesa.core :as promesa]))

(defonce state
  (atom {}))

(defn call-function
  [function-name options]
  (.then (.callFunction (:nvim @state) function-name (clj->js options))
         #(js->clj % :keywordize-keys true)))

(def clean
  (comp distinct
        (partial remove empty?)
        (partial map trim)
        split-lines))

(defn sift
  [f]
  (promesa/let [s (call-function "getreg" ["+"])]
    (->> s
         clean
         pr-str
         (spit (str f ".sift")))
    (.command (:nvim @state) (str "e " f ".sift"))))

(defn register-command
  [plugin command-name handle options]
  (.registerCommand plugin command-name #(apply handle (js->clj % :keywordize-keys true)) (clj->js options)))

(defn load
  []
  (promesa/let [buffer (.-buffer (:nvim @state))
                path (.-name buffer)]
    (if (existsSync path)
      (read-string (slurp path))
      [])))

(defn main
  [plugin]
  (reset! state {:nvim (.-nvim plugin)})
  (.registerAutocmd plugin "BufReadCmd" load (clj->js {:pattern "*.sift"
                                                       :sync true}))
  (register-command plugin "Sift" sift {:nargs 1}))
