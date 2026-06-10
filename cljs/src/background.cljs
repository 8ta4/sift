(ns background)

(defonce port
  (js/chrome.runtime.connectNative "host"))

(defn handle-host
  [message]
  (js/console.log "Message from host:")
  (js/console.log message))

(defn init
  []
  (.addListener port.onMessage handle-host))
