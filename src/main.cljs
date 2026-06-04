(ns main
  (:require [clojure.string :refer [split-lines trim]]
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
  [filename]
  (promesa/let [s (call-function "getreg" ["+"])]
    (clean s)))

(defn register-command
  [plugin command-name handle options]
  (.registerCommand plugin command-name #(apply handle (js->clj % :keywordize-keys true)) (clj->js options)))

(defn main
  [plugin]
  (reset! state {:nvim (.-nvim plugin)})
  (.registerAutocmd plugin "BufEnter" (fn []) (clj->js {:pattern "*.sift"}))
  (register-command plugin "Sift" sift {:nargs 1}))
