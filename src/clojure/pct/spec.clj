(ns pct.spec
  (:require [clojure.string :as s]
            [taoensso.timbre :as timbre]))

(defprotocol IReconSpec
  (show-spec* [this])
  (log-spec*  [this])
  (shuffle-dataset* [this ds]))

(defrecord DataSpec [^int rows       ;; rows in each slice
                     ^int cols       ;; cols in each slice
                     ^int im-size    ;; rows x cols, for future reference
                     ^int slices     ;; total number of slices
                     ])

(defrecord IndexSpec [^int jobs        ;; number of threads to be used for indexing
                      ^int batch-size  ;; batch process size
                      ^int min-length  ;; threshold for filtering path data by length
                      ^int out-buffer  ;; output channel's buffer size
                      ])

(defrecord ReconSpec [^DataSpec data
                      ^clojure.lang.PersistentVector coverage  ;; coverage range
                      ^int iterations ;; reconstruction iterations
                      ^int batch-size ;; recon batch size
                      ^int shuffle-step ;; angle to jump
                      ;; TVS stuffs
                      ^int tvs-N      ;; TVS iterations
                      ^double tvs-alpha   ;; tvs parameter
                      ;; recon parameters
                      ^double lambda  ;; art parameter
                      ;; ^doubles lambda-list ;; for use
                      ^double eta     ;; robust parameter
                      ^boolean drop    ;; Using DROP or not
                      ^boolean robust  ;; Using DROP or not
                      ^boolean sequential ;; sequential run? mostly for testing when true
                      ^ints prime-list ;; list of primes
                      ]
  java.lang.Object
  (toString [this]
    (let [{rows :rows, cols :cols, slices :slice} data]
      (s/join "\n" [(format "ROWS x COLS x SLICES : %d, %d, %d" rows cols slices)
                    (format "range           : [%d, %d]" (coverage 0) (coverage 1))
                    (format "Iterations      : %d" iterations)
                    (format "Batch Size      : %d" batch-size)
                    (format "N [tvs]         : %d" tvs-N)
                    (format "Alpha [tvs]     : %f" tvs-alpha)
                    (format "Shuffle Step    : %d (degree)" shuffle-step)
                    (format "lambda          : %f" lambda)
                    (format "eta             : %f" eta)
                    (format "Prime Range     : [%d, %d]" (aget prime-list 0) (aget prime-list (dec (alength prime-list))))
                    (format "Sequential run? : %b" sequential)
                    (format "Using drop?     : %b" drop)
                    (format "Using robust?   : %b" robust)])))
  IReconSpec
  (show-spec* [this] (println (.toString this)))
  (log-spec*  [this] (timbre/info (.toString this)))
  #_(shuffle-dataset* [_ ds]
    (let [arr (ArrayList.)]
      (doseq [d ds]
        (.addAll arr d))
      ())))
