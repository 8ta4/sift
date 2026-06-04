(ns main)

(defn main
  [plugin]
  (.registerAutocmd plugin "BufEnter" (fn []) (clj->js {:pattern "*.sift"}))
  (.registerCommand plugin "Sift" (fn [])))
