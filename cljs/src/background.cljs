(ns background
  (:require
   [goog.string :refer [format]]))

(defonce port
  (js/chrome.runtime.connectNative "host"))

(defn browse
  [text reference]
  (js/chrome.windows.create (clj->js {:url (format reference text)})))

(defn handle-host
  [message]
  (js/console.log "Message from host:")
  (js/console.log message)
  (run! (partial browse (:text (js->clj message :keywordize-keys true)))
        (:references (js->clj message :keywordize-keys true))))

(defn init
  []
  (.addListener port.onMessage handle-host))
