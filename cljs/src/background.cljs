(ns background
  (:require [com.rpl.specter :refer [ATOM setval]]
            [goog.string :refer [format]]
            [promesa.core :as promesa]))

(defonce port
  (js/chrome.runtime.connectNative "host"))

(defonce state
  (atom {}))

(defn browse
  [text reference]
  (promesa/let [window (js/chrome.windows.create (clj->js {:url (format reference text)}))]
    (setval [ATOM reference] (.-id window) state)))

(defn handle-host
  [message]
  (js/console.log "Message from host:")
  (js/console.log message)
  (run! (partial browse (:text (js->clj message :keywordize-keys true)))
        (:references (js->clj message :keywordize-keys true))))

(defn init
  []
  (.addListener port.onMessage handle-host))
