(ns pct.common
  (:use clojure.core)
  (:require [clojure.core.async :as a :refer  [<!! >!! go go-loop <! >! put! close! alts! chan timeout thread]]
            [taoensso.timbre :as timbre]
            [uncomplicate.commons.core :refer [release with-release releaseable? let-release info]])
  (:import [java.nio IntBuffer]
           [java.util ArrayList]
           [java.util.concurrent Executors Executor ThreadLocalRandom]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

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

(defn fill-sorted-seq
  "Assuming s is an sorted integer sequence, return a seq with missing numbers"
  [s]
  (let [arr ^java.util.ArrayList (java.util.ArrayList. (count s))]
    (loop [s s]
      (if-let [[a & rst] s]
        (if-let [n (first rst)]
          (if (= (- (int n) (int a)) 1)
            (do (.add arr a)
                (recur rst))
            (do (.addAll arr (range a n))
                (recur rst)))
          (.add arr a))))
    (let [v (vec (.toArray arr))]
      (.clear arr)
      v)))

(defmacro with-out-str-data-map
  [& body]
  `(let [s# (new java.io.StringWriter)]
     (binding [*out* s#]
       (let [r# ~@body]
         {:ans r#
          :str (str s#)}))))


(defn count-zeros [coll]
  (reduce (fn [acc c]
            (if (or (= c 0) (= c 0.0))
              (inc ^long acc)
              acc))
          0 coll))


(defn blocking-reduce
  "Performing reduce on a channel just like async reduce from core.async"
  [f init ch]
  (loop [ret init]
    (let [v (a/<!! ch)]
      (if (nil? v)
        ret
        (let [ret' (f ret v)]
          (if (reduced? ret')
            @ret'
            (recur ret')))))))


(defn combination [[a & rst, :as s]]
  #_(println "v0.2")
  (reduce (fn [acc v]
              (concat acc (mapv #(conj % v) acc)))
            [(sorted-set (first s)) (sorted-set)]
            (rest s)))

(defn combination-recur [[a & rst, :as s]]
  (if (= (count s) 1)
    [(sorted-set (first s)) (sorted-set)]
    (let [without-a (combination-recur rst)]
      (concat without-a (map #(conj % a) without-a)))))

(defn cont-subsets
  ([s]
   (let [_s (loop [res [s]
                   src (next s)]
              #_(timbre/info res ", " src)
              (if src
                (recur (conj res src) (next src))
                res))]
    (sort-by
     count
     (reduce (fn [acc [a & rst]]
               (concat acc (reductions conj (sorted-set a) rst)))
             []
             _s))))
  ([lo hi]
   (cont-subsets (range lo (inc (int hi))))
   #_(let [_hi (inc (int hi))]
     (sort-by
      count
      (reduce (fn [acc i]
                (concat acc (reductions conj (sorted-set i) (range (inc (int i)) _hi))))
              []
              (range lo _hi))))))


(defn shuffle-data [data ^long n ^long step]
  (let-release [temp ^objects (object-array n data)]
    (let [shuffled-data (object-array n)]
     (loop [i 0 idx ^int (int 0)]
       (if (< i n)
         (do (aset shuffled-data idx (aget temp idx))
             (recur (unchecked-inc i) (int (mod (unchecked-add-int idx step) n))))
         shuffled-data)))))


#_(defn prime-seq [n]
  (let [end (long (Math/sqrt n))
        p-list (ArrayList. [2])]
    (loop [s (drop 1 (map #(inc (* 2 ^long %)) (range)))]
      (let [p (first s)]
        (println p)
        (if (<= ^long p end)
          (do (.add p-list p)
              (recur (remove #(= (rem ^long % ^long p) 0) (drop 1 s))))
          (concat (vec p-list) s))))))

(defn prime? [^long n ^ArrayList plist]
  (every? #(not= (rem n ^long %) 0) plist))

(defn first-prime [s plist]
  (loop [s s]
    (if-let [[a & rst] s]
      (if (prime? a plist)
        [a rst]
        (recur rst))
      nil)))

(defn- prime-seq1
  [^long n] ;; looking for prime number within [2, n]
  (let [plist (ArrayList. [2 3 5 7])
        sieve (.subList plist 1 4) ;; no need to check 2 or 5's factor
        max-s (int (Math/sqrt n))
        i ^long (loop [i 11]
                  (if (<= i max-s)
                    (do (if (prime? i sieve)
                          (.add sieve i))
                        (recur (+ i 2)))
                    i))]
    (println "i = " i "sieve size = " (.size sieve))
    (let [full-plist (ArrayList. plist)]
      (loop [i ^long i]
        (if (<= i n)
          (do (if (prime? i sieve)
                (.add full-plist i))
              (recur (+ i 2)))
          full-plist)))))


(defn- prime-seq2
  [^long n] ;; looking for prime number within [2, n]
  (let [mark   (byte-array n)
        sqrt_n  (int (Math/sqrt n))]
    (loop [i (int 2)]
      (if (< i sqrt_n)
        (do
          (loop [idx (* i i)]
            (when (< idx n)
              (aset mark idx (byte 1))
              (recur (+ idx i ))))
          (let [next_i (loop [i (inc i)]
                         (if (= (aget mark i) (byte 0))
                           i
                           (recur (inc i))))]
            (recur (long next_i))))
        (let [p (ArrayList.)]
          (loop [i (int 2)]
            (if (< i n)
              (do
                (when (= (aget mark i) 0)
                  (.add p i))
                (recur (inc i)))
              p)))))))

(defn- prime-seq3
  [^long n] ;; looking for prime number within [2, n]
  (let [len    (int (+ (/ n 2.0) 0.5))
        mark   (byte-array len)
        sqrt_n (int (Math/sqrt n))]
    (loop [i (int 3)]
      (if (< i sqrt_n)
        (let [ix2 (bit-shift-left i 1)]
          (loop [idx (* i i)]
            (when (< idx n)
              (aset mark (bit-shift-right idx 1) (byte 1))
              (recur (+ idx ix2))))
          (let [next_i (loop [i (bit-shift-right (+ i 2) 1)]
                         (if (= (aget mark i) (byte 0))
                           (+ (bit-shift-left i 1) 1)
                           (recur (inc i))))]
            (recur (long next_i))))
        (let [p (ArrayList.)]
          (.add p 2)
          (loop [i (int 1)]
            (if (< i len)
              (do
                (when (= (aget mark i) 0)
                  (.add p (+ (bit-shift-left i 1) 1)))
                (recur (+ i 1)))
              ;; p ;; previously
              (int-array p))))))))


(defn- prime-seq4
  [^long n] ;; looking for prime number within [2, n]
  (let [len    (int (+ (/ n 2.0) 0.5))
        mark   (byte-array len)
        sqrt_n (int (/ (Math/sqrt n) 2))]
    (loop [i (int 1)]
      (if (< i sqrt_n)
        (do
          (when (= (aget mark i) (byte 0))
            (let [w (inc (bit-shift-left i 1))]
              (loop [idx (bit-shift-left (+ (* i i) i) 1)]
                (when (< idx len)
                  (aset mark idx (byte 1))
                  (recur (+ idx w))))))
          (recur (inc i)))
        (let [p (ArrayList.)]
          (.add p 2)
          (loop [i (int 1)]
            (if (< i len)
              (do
                (when (= (aget mark i) 0)
                  (.add p (+ (bit-shift-left i 1) 1)))
                (recur (+ i 1)))
              p)))))))


(defn firstPrime
  "Return a index of first element that is greater than x, assuming plist is sorted.
   If x is out of bound of plist, return -1.
   Unsorted data will cause -2 error during search."
  [x ^ints plist]
  (let [n (alength plist)
        last-idx (dec n)
        x (int x)]
    (cond
      (= x (aget plist 0))
      0

      (= x (aget plist last-idx))
      last-idx

      (and (> x (aget plist 0)) (< x (aget plist last-idx)))
      (loop [l 0
             r last-idx]
        (let [w (- r l)
              i (+ l (bit-shift-right w 1))
              c (aget plist i)]
          ;; (println " ==> " i ", w = " w)
          (if (< x c)
            (recur l (dec i))
            (if (>= x c)
              (let [i+1 (inc i)]
                (if (< x (aget plist i+1))
                  i+1
                  (recur i+1 r)))
              -2))))
      :else -1)))


(def prime-seq prime-seq3)


(defmacro rad2deg [r] `(/ (* 180.0 ~r) Math/PI))

(defmacro deg2rad [d] `(/ (* Math/PI ~d) 180.0))

(defn thread-reduce
  [f init ch]
  (thread
    (loop [ret init]
      (if-let [v (<!! ch)]
        (let [ret' (f ret v)]
          (if (reduced? ret')
            @ret'
            (recur ret')))
        ret))))

;; (defprotocol IMaxMin
;;   "This protocol does not work for more than one primative array"
;;   (max-min-proto [arr] "return max and min value of an array"))

;; (extend-protocol IMaxMin
;;   (class (int-array 0))
;;   ;; (Class/forName "[I")
;;   (max-min-proto [^ints arr]
;;     (let [len ^int (int (alength ^ints arr))]
;;       (loop [max ^int (int (aget ^ints arr 0))
;;              min ^int (int (aget ^ints arr 0))
;;              i   ^int (int 1)]
;;         (if (< i len)
;;           (let [v ^int (aget ^ints arr i)]
;;             (recur (int (if (> v max) v max))
;;                    (int (if (< v min) v min))
;;                    (unchecked-inc i)))
;;           [max min]))))

;;   (class (double-array 0))
;;   (max-min-proto [^doubles arr]
;;     (let [len ^int (int (alength arr))]
;;       (loop [max ^double (double (aget arr 0))
;;              min ^double (double (aget arr 0))
;;              i   ^int (int 1)]
;;         (if (< i len)
;;           (let [v ^double (aget arr i)]
;;             (recur (if (> v max) v max)
;;                    (if (< v min) v min)
;;                    (unchecked-inc i)))
;;           [max min]))))
;; )


(defmacro a-update
  [arr idx f]
  `(let [i# ~idx
         a# ~arr]
     (aset a# i# (~f (aget a# i#)))))

(defn vec-remove1 [v x]
  (loop [c v
         i ^int (int 0)]
    (let [head (first c)]
      (if head
        (let [next ^int (unchecked-inc-int i)]
          (if (= head x)
            (vec (concat (subvec v 0 i) (subvec v next)))
            (recur (rest c) next)))
        v))))

;; (def max-min nil)
(defmulti max-min (fn [arr & _] (class arr)))

(defmethod max-min (Class/forName "[I");; (class (int-array 0))
  [^ints arr]
  (let [len ^int (int (alength arr))]
    (loop [max ^int (int (aget arr 0))
           min  max
           i   ^int (int 1)]
      (if (< i len)
        (let [v ^int (int (aget arr i))]
          (recur (int (if (> v max) v max))
                 (int (if (< v min) v min))
                 (unchecked-inc i)))
        [max min]))))


(defmethod max-min (Class/forName "[D") ;; (class (double-array 0))
  [^doubles arr]
  (let [len ^int (int (alength arr))]
    (loop [max ^double (double (aget arr 0))
           min  max
           i   ^int (int 1)]
      (if (< i len)
        (let [v ^double (aget arr i)]
          (recur (double (if (> v max) v max))
                 (double (if (< v min) v min))
                 (unchecked-inc i)))
        [max min]))))


(defmethod max-min (Class/forName "[F") ;; (class (double-array 0))
  [^floats arr]
  (let [len ^int (int (alength arr))]
    (loop [max ^float (float (aget arr 0))
           min  max
           i   ^int (int 1)]
      (if (< i len)
        (let [v ^float (float (aget arr i))]
          (recur (float (if (> v max) v max))
                 (float (if (< v min) v min))
                 (unchecked-inc i)))
        [max min]))))


(defmethod max-min java.nio.ByteBufferAsIntBufferL ;; (class (double-array 0))
  [^IntBuffer int-buf  length]
  (loop [max ^int (.get int-buf 0)
         min  max
         i   ^int (int 1)]
    (if (< i ^int length)
      (let [v ^int (.get int-buf i)]
        (recur (int (if (> v max) v max))
               (int (if (< v min) v min))
               (unchecked-inc i)))
      [max min])))

;; (defn max-min-ints [^ints arr]
;;   (let [len ^int (int (alength arr))]
;;     (loop [max ^int (int (aget arr 0))
;;            min ^int (int (aget arr 0))
;;            i   ^int (int 1)]
;;       (if (< i len)
;;         (let [v ^int (aget arr i)]
;;           (recur (int (if (> v max) v max))
;;                  (int (if (< v min) v min))
;;                  (unchecked-inc i)))
;;         [max min]))))

;; (defn max-min2 [arr]
;;   (let [v (into [] arr)]
;;     [(apply max v) (apply min v)]))

;; (defn max-min3 [v]
;;   [(apply max v) (apply min v)])

(defmulti mean (fn [arr] (class arr)))

(defmethod ^double mean (Class/forName "[I")
  [^ints arr]
  (let [len (alength arr)]
    (loop [i ^int (int 0)
           sum ^double (double 0.0)]
      (if (< i len)
        (recur (unchecked-inc-int i) (+ sum (aget arr i)))
        (double (/ sum len))))))


(defmulti std (fn [arr] (class arr)))

(defmethod std (Class/forName "[I")
  [^ints arr]
  (let [m ^double (mean arr)
        len ^int (alength arr)]
    (loop [i ^int (int 0)
           sum ^double (double 0.0)]
      (if (< i len)
        (let [x-m (- (double (aget arr i)) ^double m)]
         (recur (unchecked-inc-int i) (+ sum (* x-m x-m))))
        (Math/sqrt ^double (/ sum (unchecked-dec-int len)))))))


(defmulti histogram (fn [arr & _] (class arr)))

(defmethod histogram (Class/forName "[I")
  [^ints arr & {:keys [bins]}]
  (let [histogram ^ints (or bins (int-array (inc ^int (first (max-min arr))) 0))
        len       ^int  (alength arr)]
    ;; histogram
    (loop [i ^int (int 0)]
      (if (< i len)
        (do
          (a-update ^ints histogram ^int (aget arr i) unchecked-inc-int)
          (recur (unchecked-inc-int i)))
        histogram))))



(defmacro array-add
  [to from]
  `(let [len# (alength ~to)]
     (loop [i# (int 0)]
       (if (< i# len#)
         (do
           (aset ~to i# (+ (aget ~to i#) (aget ~from i#)))
           (recur (unchecked-inc-int i#)))
         ~to))))


(defmacro print-array
  [arr]
  `(dotimes [i# (alength ~arr)]
     (println (format "%d : 0x%x" i# (aget ~arr i#)))))

(defn sum-array
  [^ints arr]
  (let [len ^int (alength arr)]
   (loop [i   ^int (int 0)
          sum ^int (int 0)]
     (if (< i len)
       (recur (unchecked-inc-int i) (unchecked-add-int sum (aget arr i)))
       sum))))

(defn histogram-parallel
  [^ints arr & {:keys [bins n]}]
  (let [n          ^int  (int (or n 1))
        histogram  ^ints (or bins (int-array (inc ^int (first (max-min arr))) 0))
        len        ^int  (alength arr)
        block-size ^int  (int (Math/ceil (/ len n)))
        res (vec (repeatedly (int n) chan))
        hist-fn (fn [idx [^int start ^int length]]
                  (let [end (+ start length)
                        local-hist ^ints (int-array (alength ^ints histogram) 0)]
                    (loop [i ^int (int start)]
                      (if (< i end)
                        (do
                          (a-update local-hist (aget arr i) unchecked-inc-int)
                          (recur (unchecked-inc-int i)))
                        (do
                          (a/put! (res idx) local-hist)
                          (a/close! (res idx)))))))]

    (let [last-idx (dec n)]
      (loop [i ^int (int 0)]
        (let [start-idx (* i block-size)]
          (if (< i last-idx)
            (do (go (hist-fn i [start-idx block-size]))
                (recur (unchecked-inc-int i)))
            (go (hist-fn i [start-idx (- len start-idx) ]))))))

    ;; summing all the sub histogram
    (loop [ch res]
      (if (not-empty ch)
        (let [[a p] (a/alts!! ch)]
          ;; (print-array histogram)
          (array-add ^ints histogram ^ints a)
          (recur (vec-remove1 ch p)))
        histogram))))


(defn random-int-array
  "Return a random array"
  [^long n]
  (let [rand ^ThreadLocalRandom (ThreadLocalRandom/current)
        a ^ints (int-array n)]
    (loop [i ^long (long 1)]
      (if (= i n)
        a
        (let [j ^int (.nextInt rand (unchecked-inc i))]
          (aset a i (aget a j))
          (aset a j i)
          (recur (unchecked-inc i)))))))

