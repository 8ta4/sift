(ns main)

(defonce state
  (atom {}))

(defn main
  [plugin]
  (reset! state {:nvim (.-nvim plugin)})
  (.registerAutocmd plugin "BufEnter" (fn []) (clj->js {:pattern "*.sift"}))
  (.registerCommand plugin "Sift" (fn [])))
