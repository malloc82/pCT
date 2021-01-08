(ns pct.logging
  (:require [clojure.string :as str]
            [taoensso.timbre :as timbre]
            [taoensso.encore :as enc]
            [clojure.java.io :as io]
            [clojure.core.async :as async :refer [<!! >!! close! chan thread go]]))


(defn async-appender
  [& [{:keys [channel path append?]
       :or {channel (chan 1024)
            path "./timbre-async-spit.log"
            append? true}}]]
  {:enabled? true
   :async?   true
   :mini-level nil
   :rate-limit nil
   :output-fn :inherit
   :fn
   (do (async/thread
         (let [log (io/file path)]
           (when-not (.exists log)
             (io/make-parents log))
           (let [{:keys [pattern locale timezone]} (:timestamp-opts timbre/*config*)
                 fmt ^java.text.SimpleDateFormat (enc/simple-date-format* pattern locale timezone)
                 host (try (.getHostName (java.net.InetAddress/getLocalHost))
                           (catch java.net.UnknownHostException _ nil))]
             (spit path (format "%s %s INFO %s" (.format fmt (enc/now-dt)) host "Async spit thread started.\n") :append true)
             (loop []
               (if-let [data (<!! channel)]
                 (do
                   (spit path (str (force data) "\n") :append true)
                   (recur))
                 (spit path (format "%s %s INFO %s" (.format fmt (enc/now-dt)) host
                                    "Log channel closed ... async logging thread stoped.\n")
                       :append true)
                 ;; (spit path "\nLog channel closed ... logging stoped.\n" :append true)
                 )))))
       (fn [data]
         (async/put! channel (:output_ data))))})

