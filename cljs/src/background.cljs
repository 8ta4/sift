(ns background
  (:require [com.rpl.specter :refer [ATOM MAP-VALS NONE setval]]
            [cuerdas.core :refer [format]]
            [promesa.core :as promesa]))

(defonce port
  (js/chrome.runtime.connectNative "host"))

(defonce state
  (atom {}))

(defn browse
  [text reference]
  (if-let [id (@state reference)]
    (promesa/let [active-tabs (js/chrome.tabs.query (clj->js {:windowId id
                                                              :active true}))
                  inactive-tabs (js/chrome.tabs.query (clj->js {:windowId id
                                                                :active false}))]
      (-> active-tabs
          (js->clj :keywordize-keys true)
          first
          :id
          (js/chrome.tabs.update (clj->js {:url (format reference text)})))
      (run! (comp js/chrome.tabs.remove
                  :id)
            (js->clj inactive-tabs :keywordize-keys true)))
    (promesa/let [window (js/chrome.windows.create (clj->js {:url (format reference text)}))]
      (setval [ATOM reference] (.-id window) state))))

(defn handle-host
  [message]
  (run! (partial browse (:text (js->clj message :keywordize-keys true)))
        (:references (js->clj message :keywordize-keys true))))

(defn init
  []
  (.addListener port.onMessage handle-host)
  (js/chrome.windows.onRemoved.addListener #(setval [ATOM MAP-VALS (partial = %)] NONE state)))
