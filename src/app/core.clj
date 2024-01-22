(ns app.core (:require [io.pedestal.http :as http]
                       [environ.core :refer [env]]))

(defn unlimited [_] {:status 200 :body "unlimited! lets go!"})
(defn limited [_] {:status 200 :body "limited, dont over use me"})

(def ip-buckets (atom {}))
(def bucket-default
  {:tokens 10
   :last-refill (System/currentTimeMillis)})

(defn handle-token-bucket [ip]
  (let [now (System/currentTimeMillis)
        bucket (get @ip-buckets ip {:tokens 0 :last-refill now})
        elapsed-ms (- now (:last-refill bucket))
        tokens-to-add (int (/ elapsed-ms 1000))
        updated-tokens (min (+ (:tokens bucket) tokens-to-add) 10)]
    (println "yerrr" elapsed-ms)
    (if (> elapsed-ms 0)
      (swap! ip-buckets assoc ip {:tokens updated-tokens :last-refill now})
      @ip-buckets)))

(defn get-bucket [ip]
  (let [bucket (ip @ip-buckets)]
    (if (nil? bucket)
      (do (swap! ip-buckets #(assoc % ip bucket-default))
          (handle-token-bucket ip))
      (handle-token-bucket ip))))

(defn handle-request [request]
  (let [ip-address (keyword (-> request :remote-addr str))
        bucket (get-bucket ip-address)
        values (ip-address bucket)]
    (if (pos? (:tokens values))
        (do
          @ip-buckets
          (println ip-buckets)
          (swap! ip-buckets update-in [ip-address :tokens] dec)
          (println "sooo" @ip-buckets)
          {:status 200 :body "Request Excepted"})
        {:status 429 :body "Too many request"})))

(def routes #{["/limited" :get handle-request
               :route-name :limited] ;Routes
              ["/unlimited" :get unlimited :route-name :unlimited]})

(def service-map (-> {::http/routes routes
                       ::http/type   :immutant
                       ::http/host   "0.0.0.0"
                       ::http/join?  false
                       ::http/port   (Integer. (or (env :port) 8080))}
                      http/default-interceptors
                      (update ::http/interceptors into [http/json-body])))

(defn -main [_] (-> service-map http/create-server http/start)) ; Server Instance
