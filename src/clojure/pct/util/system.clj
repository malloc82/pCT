(ns pct.util.system)

(defn get-timestamp [] (.format (java.text.SimpleDateFormat. "YYYY-MM-dd'T'HH-mm-ss_Z") (java.util.Date.)))

(defn ns-all-vars[n]
  (filter (fn[[_ v]]
            (and (instance? clojure.lang.Var v)
                 (= n (.getName ^clojure.lang.Namespace (.ns ^clojure.lang.Var v)))))
          (ns-map n)))

(defn ns-publics-list [ns] (#(list (ns-name %) (map first (ns-publics %))) ns))

(defn localhost []
  (java.net.InetAddress/getLocalHost))

(defn memorySize []
  (loop [m ^double (double (.maxMemory (Runtime/getRuntime)))
         s ["B" "KB" "MB" "GB" "TB"]]
    (let [unit (first s)]
      (if (or (= unit "TB") (< m 1024))
        (println (format "%.1f %s" m unit))
        (recur (/ m 1024) (rest s))))))
