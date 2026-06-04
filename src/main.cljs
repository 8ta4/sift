(ns main)

(defn main
  [plugin]
  (.registerCommand plugin "Sift" (fn [])))
