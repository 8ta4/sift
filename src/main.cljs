(ns main
  (:require [cljs-node-io.core :refer [spit]]
            [clojure.string :refer [split-lines trim]]
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
    (spit (str f ".sift") (pr-str (clean s)))
    (.command (:nvim @state) (str "e " f ".sift"))))

(defn register-command
  [plugin command-name handle options]
  (.registerCommand plugin command-name #(apply handle (js->clj % :keywordize-keys true)) (clj->js options)))

(defn main
  [plugin]
  (reset! state {:nvim (.-nvim plugin)})
  (.registerAutocmd plugin "BufReadCmd" (fn []) (clj->js {:pattern "*.sift"}))
  (register-command plugin "Sift" sift {:nargs 1}))
