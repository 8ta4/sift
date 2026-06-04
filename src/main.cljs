(ns main)

(defonce state
  (atom {}))

(defn call-function
  [fname args]
  (.then (.callFunction (:nvim @state) fname (clj->js args))
         #(js->clj % :keywordize-keys true)))

(defn sift
  []
  (call-function "getreg" ["+"]))

(defn main
  [plugin]
  (reset! state {:nvim (.-nvim plugin)})
  (.registerAutocmd plugin "BufEnter" (fn []) (clj->js {:pattern "*.sift"}))
  (.registerCommand plugin "Sift" sift))
