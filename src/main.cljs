(ns main
  (:require [clojure.string :refer [join split-lines trim]]
            [promesa.core :as promesa]))

(defonce state
  (atom {}))

(defn call-function
  [fname args]
  (.then (.callFunction (:nvim @state) fname (clj->js args))
         #(js->clj % :keywordize-keys true)))

(def clean
  (comp (partial join "\n")
        distinct
        (partial remove empty?)
        (partial map trim)
        split-lines))

(defn sift
  []
  (promesa/let [s (call-function "getreg" ["+"])]
    (clean s)))

(defn main
  [plugin]
  (reset! state {:nvim (.-nvim plugin)})
  (.registerAutocmd plugin "BufEnter" (fn []) (clj->js {:pattern "*.sift"}))
  (.registerCommand plugin "Sift" sift))
