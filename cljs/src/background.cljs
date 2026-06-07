(ns background)

(defonce port
  (js/chrome.runtime.connectNative "host"))

(defn init
  [])
