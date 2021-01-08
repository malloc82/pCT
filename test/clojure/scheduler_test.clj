(ns util.scheduler-test
  (:require [clojure.core.async :as a]
            [util.scheduler :as s]))


(def scheduler (s/newScheduler))

(defn ^:private test-job-fn1 [comm-ch n]
  (a/thread
    (let [v (a/<!! comm-ch)]
      (println (format "Test job started, sleeping for %ds ..." n))
      (Thread/sleep (* n 1000))
      (println "Test job finished.")
      "Done.")))

(defn ^:private test-job-fn2 [comm-ch n]
  (a/thread
    (let [v (a/<!! comm-ch)]
      (println (format "Test job started, sleeping for %ds ..." n))
      (Thread/sleep (* n 1000))
      (throw (Exception. "Exception test"))
      (println "Test job finished.")
      "Done.")))



(s/status scheduler)

(s/submit scheduler [test-job-fn1 [(a/chan 1) (rand-int 10)]])
(s/submit scheduler [test-job-fn1 [(a/chan 1) (rand-int 10)]])
(s/submit scheduler [test-job-fn2 [(a/chan 1) (rand-int 10)]])
(s/submit scheduler [test-job-fn1 [(a/chan 1) (rand-int 10)]])
(s/submit scheduler [test-job-fn1 [(a/chan 1) (rand-int 10)]])

(s/start scheduler)
(s/status scheduler)

(s/show-queue scheduler)

(s/show-history scheduler)

(s/clear-queue! scheduler)

(s/show-queue scheduler)

(s/submit scheduler [test-job-fn [(a/chan 1) (rand-int 10)]])
(s/submit scheduler [test-job-fn [(a/chan 1) (rand-int 10)]])
(s/submit scheduler [test-job-fn [(a/chan 1) (rand-int 10)]])


(s/shutdown! scheduler)
