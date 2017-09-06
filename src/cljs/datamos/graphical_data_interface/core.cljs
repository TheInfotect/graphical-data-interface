(ns datamos.graphical-data-interface.core
  (:require [reagent.core :as reagent :refer [atom]]
            [re-frame.core :as rf]
            [day8.re-frame.async-flow-fx :as afx]
            [cljs.core.async :as async]
            [taoensso.encore :as encore :refer-macros [have have?]]
            [taoensso.timbre :as timbre :refer-macros [tracef debugf infof warnf errorf]]
            [taoensso.sente :as sente :refer [cb-success?]]))

(enable-console-print!)

(defonce http-router (atom nil))

;;;; Util for logging output to on-screen console

(def output-el (.getElementById js/document "output"))

(comment
  (->output! "ClojureScript has loaded correctly"))

(let [packer :edn
      {:keys [chsk ch-recv send-fn state]} (sente/make-channel-socket-client! "/chsk"
                                                                              {:host   "localhost:8000"
                                                                               :packer packer})]
  (def chsk chsk)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket send API fn
  (def chsk-state state))                                   ; Watchable read-only atom

(add-watch chsk-state :sente-state
           (fn [key reference old-state new-state]
             (infof "add-watch on chsk-state: %s %s %s %s" key reference old-state new-state)))

;;;; Sente event-handlers

(defmulti -event-msg-handler
          "Multimethod to handle Sente event messages"
          :id)                                              ; Dispatch on event-id

(defn event-message-handler
  "Wraps -event-msg-handler with logging, error catching, etc."
  [{:as ev-msg :keys [id ?data event]}]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler
  :default
  [{:as ev-msg :keys [event]}]
  (comment (->output! "Unhandled event: %s" event)))

(defmethod -event-msg-handler
  :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state-map new-state-map] (have vector? ?data)]
    (if (:first-open? new-state-map)
      (infof "Channel socket succesfully established: %s" new-state-map)
      (infof "Channel socket state change: %s" new-state-map))))

(defmethod -event-msg-handler
  :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (comment
    (->output! "Push event from server: %s" ?data)))

(defmethod -event-msg-handler
  :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (comment
      (->output! "Handshake: %s" ?data))))

(defn stop-router
  []
  (when-let [stop-fn @http-router]
    (stop-fn)))

(defn start-router
  []
  (reset! http-router
          (sente/start-client-chsk-router! ch-chsk event-message-handler)))

(defn reset
  []
  (stop-router)
  (start-router))

(rf/reg-event-db
  :initialize
  (fn [_ _]
    {:datamos/quads
     {:datamos-qry/query-context-graph01
                                 {:datamos-qry/select-query01 {:dmsqry-def/has-query-context-graph :datamos-qry/query-context-graph01
                                                               :rdf/type                           :dmsqry-def/select-query
                                                               :dmsqry-def/has-solution-modifier   :dmsqry-def/distinct
                                                               :dmsqry-def/result-variable         :dmsqry-def/all
                                                               :dmsqry-def/has-variable            :datamos-var/var01
                                                               :dmsqry-def/has-query-graph         :datamos-qry/query-graph01
                                                               :dmsqry-def/has-clause              :datamos-qry/limit-01}
                                  :datamos-qry/limit-01       {:rdf/type             :dmsqry-def/limit
                                                               :dmsqry-def/has-value 50}}
      :datamos-qry/query-graph01 {{} {:rdf/type :datamos-var/var01}}}}))

(defn send-msg-flow
  []
  {:id             :datamos/send-msg-flow
   :db-path        [:datamos/msg-flow-state]
   :first-dispatch [:datamos/send-message]
   :rules          [{:when :seen? :events [:datamos/success-send-message] :dispatch [:datamos/success-message]}
                    {:when :seen? :events [:datamos/fail-send-message] :dispatch [:datamos/failed-message]}]})

(rf/reg-event-fx
  :datamos/success-message
  (fn [a b]
    (infof "Apperently we have a success to celebrate :datamos/success-message: %s %s" a b)))

(rf/reg-event-fx
  :datamos/failed-message
  (fn [a b]
    (infof "Oops we (as in we the system) think something went wrong: %s %s" a b)))

(rf/reg-event-fx
  :datamos/sending-msgs
  (fn [cofx [_ msg]]
    (infof "Data received [:datamos/sending-msgs]: %s %s" cofx msg)
    {:db         (assoc (:db cofx) :datamos/request {:datamos/key msg})
     :async-flow (send-msg-flow)}))

(rf/reg-fx
  :sente
  (fn [args]
    (let [{:keys [message]} args]
      (chsk-send! message))))

(rf/reg-event-fx
  :datamos/send-message
  (fn [cofx _]
    (let [db  (:db cofx)]
      {:sente {:message [:datamos/rdf-message db]}})))

(defn data-canvas
  []
  [:span
   ;; (pr-str @s)
   [:form
    [:button {:on-click #(do
                           (.preventDefault %)
                           (rf/dispatch [:datamos/send-message :datamos/quads]))}
     "Send Query"]]])

(defn ui
  []
  [:div
   [data-canvas]])

(defn start
  []
  (def _idb (rf/dispatch-sync [:initialize]))
  (reagent/render [ui]
                  (js/document.getElementById "app")))

(defn on-js-reload
  []
  (rf/clear-subscription-cache!)
  (start))