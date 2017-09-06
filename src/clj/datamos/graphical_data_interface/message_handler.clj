(ns datamos.graphical-data-interface.message-handler
  (:require [taoensso.timbre :as timbre :refer [tracef debugf infof warnf errorf]]
            [taoensso.sente :as sente]
            [datamos
             [communication :as dcom]
             [base :as base]
             [messaging :as dmsg]]))

(defmulti -event-msg-handler
          "Multimethod to handle Sente event messages"
          :id)                                                     ; Dispatch on event id

(defn event-message-handler
  "Wraps -event-msg-handler with logging, error catching etc."
  [{:as ev-msg :keys [id ?data event]}]
  (when-not (= event [:chsk/ws-ping])
    (-event-msg-handler ev-msg)))

(defmethod -event-msg-handler
  :default
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (let [session (:session ring-req)
        uid (:uid session)])
  (debugf "Unhandled event: %s" event))

(defmethod -event-msg-handler
  :datamos/rdf-message
  [{:as ev-msg :keys [event id ?data ring-req ?reply-fn send-fn]}]
  (debugf "Received message: %s \n -data: %s" ev-msg ?data)
  (dcom/speak dcom/speak-connection base/component dmsg/exchange :dmsfn-def/sparql-builder :dmsfn-def/core :datamos-fn/build-query ?data))