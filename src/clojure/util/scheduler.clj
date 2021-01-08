(ns util.scheduler
  (:require [clojure.core.async :as a]
            [clojure.core.async.impl.mutex :as mutex]
            [taoensso.timbre :as timbre]
            [util.buffer :as b])
  (:import [java.util LinkedList]
           [java.util.concurrent.locks Lock]))

(defprotocol IScheduler
  (submit       [this item])
  (status       [this])
  (current-job  [this])
  (show-queue   [this])
  (clear-queue! [this])
  (show-history [this])
  (show-error   [this]))

(defprotocol IController
  (start     [this])
  (pause     [this])
  (continue  [this])
  (shutdown! [this]))


(deftype Scheduler [id state current cmd-ch
                    queue-ch queue-buf history history-buf
                    error]
  IScheduler
  (submit [this item]
    (let [n (long @id)]
      (swap! id inc)
      (a/put! queue-ch [n item])))
  (show-queue [this]
    (b/inspect queue-buf))
  (clear-queue! [this]
    (b/clear! queue-buf))
  (status [this] @state)
  (current-job [this] @current)
  (show-history [this]
    (vec (b/inspect history-buf)))
  (show-error [this]
    (println @error))

  IController
  (start [this]
    (when (= @state :INIT)
      (reset! state :RUNNING)
      (reset! error nil)
      (a/go
        (try
          (loop []
            (when-let [cmd (a/poll! cmd-ch)]
              (case cmd
                :PAUSE    (do (reset! state :PAUSED)
                              (timbre/info "Scheduler paused.")
                              (loop []
                                (let [c (a/<! cmd-ch)]
                                  (if (= c :CONTINUE)
                                    (do (reset! state :RUNNING)
                                        (timbre/info "Scheduler resume running."))
                                    (recur)))))
                :CONTINUE (do ) ;;
                :SHUTDOWN (do (reset! state :HALT))
                (do (timbre/info (format "Unknown command: [%s]" cmd)))))
            (case @state
              :RUNNING (let [job (a/<! queue-ch)]
                         (if (= job :NOOP)
                           (recur)
                           (let [[n [f args]] job]
                             (reset! current job)
                             (let [comm-ch   (first args)
                                   result-ch (apply f args)
                                   _ (a/put! comm-ch "start")
                                   result (a/<! result-ch)]
                               (timbre/info (format "Job [%d] finished, result: %s" n result))
                               (a/put! history {:SUCCESS [n [f args]]})
                               (reset! current nil)
                               (recur)))))
              :HALT    (do (timbre/info (format "Scheduler halted.")))))
          (catch Exception e
            (do (timbre/info "Scheduler Error ...")
                (reset! state :ERROR)
                (reset! error e)))))))
  (pause     [this]
    (a/put! cmd-ch   :PAUSE)
    (a/put! queue-ch :NOOP))
  (continue  [this]
    (a/put! cmd-ch :CONTINUE))
  (shutdown! [this]
    (a/put! cmd-ch   :SHUTDOWN)
    (a/put! queue-ch :NOOP)))

(defn newScheduler []
  (let [qbuf (b/transparent-buffer)
        hbuf (b/transparent-buffer)]
    (Scheduler. (atom 0) (atom :INIT) (atom nil) (a/chan 10)
                (a/chan qbuf) qbuf (a/chan hbuf) hbuf
                (atom nil))))


(defn ^:private test-job-fn [comm-ch n]
  (a/thread
    (let [v (a/<!! comm-ch)]
      (println (format "Test job started, sleeping for %ds ..." n))
      (Thread/sleep (* n 1000))
      (println "Test job finished.")
      "Done.")))

;; atom vs mutex test

;; (def a1 (atom (LinkedList.)))
;; (time (dotimes [_ 1000]
;;         (dotimes [i 1000]
;;           (swap! a1 (fn [x] (.addFirst ^LinkedList x i) x)))
;;         (dotimes [_ 1000]
;;           (swap! a1 (fn [x] (.removeLast ^LinkedList x) x)))))
;; ;; "Elapsed time: 61.359019 msecs"



;; (def a2 (LinkedList.))
;; (def q2 (mutex/mutex))
;; (time (dotimes [_ 1000]
;;         (dotimes [i 1000]
;;           (.lock q2)
;;           (.addFirst ^LinkedList a2 i)
;;           (.unlock q2))
;;         (dotimes [_ 1000]
;;           (.lock q2)
;;           (.removeLast ^LinkedList a2)
;;           (.unlock q2))))
;; ;; "Elapsed time: 1476.565479 msecs"

