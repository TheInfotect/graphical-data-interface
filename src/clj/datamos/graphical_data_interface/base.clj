(ns datamos.graphical-data-interface.base
  (:require [mount.core :as mnt :refer [defstate]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clojure.core.async :as async]
            [ring.middleware
             [defaults :refer [wrap-defaults site-defaults]]]
            [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [org.httpkit.server :as http]
            [taoensso.timbre :as timbre :refer [tracef debugf infof warnf errorf]]
            [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
            [datamos.core :as dc]
            [datamos.graphical-data-interface.message-handler :as msgh]))

(defonce http-server (atom nil))                      ; Watchable, read-only atom
(defonce http-router (atom nil))

(let [packer :edn
      chsk-server (sente/make-channel-socket-server!
                    (get-sch-adapter) {:packer packer})
      {:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]} chsk-server]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def connected-uids connected-uids))                      ; Wachtachable read only atom

(add-watch connected-uids :connected-uids
           (fn [_ _ old new]
             (when (not= old new)
               (infof "Connected uids change: %s" new))))

(defn pr-channel
  [r]
  (infof "Received message on /msg, totally unexpected. %s" r))

(defroutes handler
           (GET "/" req {:status 200
                         :headers {"Content-Type" "text/plain"}
                         :body "And this is just another response"})
           (GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
           (POST "/chsk" req (ring-ajax-post req))
           (POST "/msg" req (pr-channel req))
           (route/not-found "Nothing to be found here, try something else"))

(def gdi-base
  (wrap-defaults handler site-defaults))

(defn stop-router []
  (when-let [stop-fn @http-router] (stop-fn)))

(defn start-router []
  (reset! http-router (sente/start-server-chsk-router! ch-chsk msgh/event-message-handler)))

(defstate http-router-server
          :start (start-router)
          :stop (stop-router))

(defn stop-server []
  (when-not (nil? http-server)
    (@http-server :timeout 100)
    (reset! http-server nil)))

(defn start-server []
  (reset! http-server (http/run-server #'gdi-base {:port 8000})))

(defstate http-io-server
          :start (start-server)
          :stop (stop-server))

(defn reset []
  (mnt/stop)
  (refresh :after 'mnt/start))

(defn -main []
  (mnt/start))

