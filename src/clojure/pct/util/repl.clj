(ns pct.util.repl
  (:require nrepl.cmdline pct.logging clojure.core.async taoensso.timbre clojure.core.async))

(defonce ^:private log-chan (clojure.core.async/chan 64))

;; Copied from nrepl.cmdline
(defn- clean-up-and-exit
  "Performs any necessary clean up and calls `(System/exit status)`."
  [status]
  (shutdown-agents)
  (flush)
  (binding [*out* *err*] (flush))
  (System/exit status))

;; Copied from nrepl.cmdline
(defn- handle-interrupt
  [signal]
  (let [transport (:transport @nrepl.cmdline/running-repl)
        client (:client @nrepl.cmdline/running-repl)]
    (if (and transport client)
      (doseq [res (nrepl.core/message client {:op "interrupt"})]
        (when (= ["done" "session-idle"] (:status res))
          (System/exit 0)))
      (System/exit 0))))

(defn start-repl
  [{:keys [interactive connect color bind host port ack-port handler middleware transport verbose repl-fn greeting]
    :as options}]
  (try
    (taoensso.timbre/merge-config! {:timestamp-opts {:pattern "yyyy-MM-dd @ HH:mm:ss Z"
                                            :locale  :jvm-default
                                            :timezone (java.util.TimeZone/getTimeZone "America/Chicago")}
                           :appenders
                           {;; :println (timbre/println-appender {:stream :auto})
                            :println nil
                            ;; :spit (appenders/spit-appender {:fname "./log/timbre-spit.log"})
                            ;; :spit (rotor/rotor-appender {:path "./log/messages.log"})
                            :spit (pct.logging/async-appender {:channel log-chan :path "./log/messages.log"})
                            }})
    (taoensso.timbre/info " >>>>>>>>>>>>>>> * new repl starts at port" port "* <<<<<<<<<<<<<<<")
    (nrepl.cmdline/set-signal-handler! "INT" handle-interrupt)
    (nrepl.cmdline/dispatch-commands options)
    (catch clojure.lang.ExceptionInfo ex
      (let [{:keys [::kind ::status]} (ex-data ex)]
        (when (= kind ::exit)
          (clean-up-and-exit status))
        (throw ex)))))

(defn hello [kv]
  (println "Hello, " kv))

(defn -main
  [& args]
  (try
    (nrepl.cmdline/set-signal-handler! "INT" handle-interrupt)
    (let [[options _args] (nrepl.cmdline/args->cli-options args)]
      (nrepl.cmdline/dispatch-commands options))
    (catch clojure.lang.ExceptionInfo ex
      (let [{:keys [::kind ::status]} (ex-data ex)]
        (when (= kind ::exit)
          (clean-up-and-exit status))
        (throw ex)))))
