(ns pct.io
  (:require [clojure.java.io :as io]
            [clojure.string  :as s]
            [taoensso.timbre :as timbre]
            [uncomplicate.neanderthal
             [core   :refer :all]
             [native :refer :all]])
  (:import [java.nio ByteOrder ByteBuffer IntBuffer]
           [java.io FileOutputStream BufferedOutputStream BufferedInputStream RandomAccessFile File]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defonce header-length ^int (count (str (Integer/MAX_VALUE))))


(defn backup [f]
  (let [fname (.getName ^File f)
        f2 ^File (File. (format "%s.old" fname))]
    (if (.exists f2)
      (loop [n 1]
        (let [f3 ^File (File. (format "%s.old.%d" fname n))]
          (if (.exists f3)
            (recur (inc n))
            (do
              (println (.getName f3))
              (.renameTo ^File f f3)))))
      (.renameTo ^File f f2))))

(defn backupFile [file]
  (let [name (.getName ^File file)
        old ^File (File. (format "%s.old" name))]
    (when (.exists old)
      (letfn [(fix-name [curr ^long n]
                (let [target ^File (File. (format "%s.old.%d" name n))]
                  (if (.exists target)
                    (do
                      (fix-name target (inc n))
                      (.renameTo ^File curr target))
                    (.renameTo ^File curr target))))]
        (fix-name old 1)))
    (.renameTo ^File file old)))


#_(defn renameFile2
  "The same thing, but using loop instead of recursion"
  [file]
  (let [name (.getName ^File file)
        old ^File (File. (format "%s.old" name))]
    (when (.exists old)
      (let [flist (loop [n 1
                         flist '()]
                    (let [f (File. (format "%s.old.%d" name n))]
                      #_(println (.getName f))
                      (if (.exists ^File f)
                        (recur (inc n) (conj flist f))
                        (conj flist f))))]
        (loop [flist flist]
          (when-let [[a & rst] flist]
            #_(println flist)
            (if-let [b (first rst)]
              (do
                (.renameTo ^File b ^File a)
                (recur rst))
              (.renameTo ^File old ^file a))))))
    (.renameTo ^File file old)))

(defmacro read-int
  ([is] `(read-int ~is nil))
  ([is save-buf]
   (let [buffer (gensym)]
     `(let [~(with-meta buffer {:tag "bytes"}) (byte-array 4)]
        (when (not= (.read ~is ~buffer) -1)
          ~(if save-buf
             `[(int (-> (ByteBuffer/wrap ~buffer)
                        (.order ByteOrder/LITTLE_ENDIAN)
                        (.getInt)))
               ~buffer]
             `(int (-> (ByteBuffer/wrap ~buffer)
                       (.order ByteOrder/LITTLE_ENDIAN)
                       (.getInt)))))))))


(defmacro write-int [os]
  (let [buffer (gensym)]
    `(let [~(with-meta buffer {:tag "bytes"}) (byte-array 4)]
       (.write ~os ~buffer)
       [(int (-> (ByteBuffer/wrap ~buffer)
                 (.order ByteOrder/LITTLE_ENDIAN)
                 (.getInt)))
        ~buffer])))

(defmacro read-float
  ([is] `(read-float ~is nil))
  ([is save-buf]
   (let [buffer (gensym)]
     `(let [~(with-meta buffer {:tag "bytes"}) (byte-array 4)]
        (when (not= (.read ~is ~buffer) -1)
          ~(if save-buf
             `[(float (-> (ByteBuffer/wrap ~buffer)
                          (.order ByteOrder/LITTLE_ENDIAN)
                          (.getFloat)))
               ~buffer]
             `(float (-> (ByteBuffer/wrap ~buffer)
                         (.order ByteOrder/LITTLE_ENDIAN)
                         (.getFloat))))))))
  #_`(let [buffer# (byte-array 4)]
     (.read ~is buffer#)
     [(float (-> (ByteBuffer/wrap buffer#)
                 (.order ByteOrder/LITTLE_ENDIAN)
                 (.getFloat)))
      buffer#]))

(defmacro read-floats
  ([is n] `(read-float ~is ~n nil))
  ([is n save-buf]
   (let [buffer (gensym)]
     `(let [~(with-meta buffer {:tag "bytes"}) (byte-array (* 4 ~n))]
        (when (not= (.read ~is ~buffer) -1)
          ~(if save-buf
             `[(-> (ByteBuffer/wrap ~buffer)
                   (.order ByteOrder/LITTLE_ENDIAN)
                   (.asFloatBuffer))
               ~buffer]
             `(-> (ByteBuffer/wrap ~buffer)
                  (.order ByteOrder/LITTLE_ENDIAN)
                  (.asFloatBuffer)))))))
  #_`(let [buffer# (byte-array 4)]
     (.read ~is buffer#)
     [(float (-> (ByteBuffer/wrap buffer#)
                 (.order ByteOrder/LITTLE_ENDIAN)
                 (.getFloat)))
      buffer#]))

(defmacro read-double
  ([is] `(read-double ~is nil))
  ([is save-buf]
   (let [buffer (gensym)]
     `(let [~(with-meta buffer {:tag "bytes"}) (byte-array 8)]
        (when (not= (.read ~is ~buffer) -1)
          ~(if save-buf
             `[(double (-> (ByteBuffer/wrap ~buffer)
                           (.order ByteOrder/LITTLE_ENDIAN)
                           (.getDouble)))
               ~buffer]
             `(double (-> (ByteBuffer/wrap ~buffer)
                          (.order ByteOrder/LITTLE_ENDIAN)
                          (.getDouble)))))))))

#_(defmacro read-double [is]
  `(let [buffer# (byte-array 8)]
     (.read ~is buffer#)
     [(double (-> (ByteBuffer/wrap buffer#)
                  (.order ByteOrder/LITTLE_ENDIAN)
                  (.getDouble)))
      buffer#]))


(defmacro int-buffer [i]
  `(let [buf# ^bytes (byte-array 4)]
     (.put ^IntBuffer (-> (ByteBuffer/wrap buf#)
                          (.order ByteOrder/LITTLE_ENDIAN)
                          .asIntBuffer)
           0 ~i)
     buf#))

(defn int-array-buffer [^ints arr]
  (let [len ^int (alength arr)
        buf ^bytes (byte-array (* 4 len))
        byte-buf ^IntBuffer (-> (ByteBuffer/wrap buf)
                                 (.order ByteOrder/LITTLE_ENDIAN)
                                 .asIntBuffer)]
     (loop [i ^int (int 0)]
       (if (< i len)
         (do
           (.put byte-buf i (aget arr i))
           (recur (unchecked-inc-int i)))
         buf))))


(defn read-header
  "Header format: number of histories is store as string, followed with a new line (0x0a)"
  [^java.io.BufferedInputStream is & {:keys [size]
                                      :or   {size (inc ^int header-length)}}]
  (let [end-idx (unchecked-dec-int size)]
    (loop [c      ^byte  (unchecked-byte (.read is))
           header ^bytes (byte-array size)
           idx    ^int   (int 0)]
      (if (or (>= idx end-idx)
              (= c (byte 10))) ;; (byte 10) is \n
        (Integer/parseInt (String. header 0 idx))
        (do
          (aset header idx c)
          (recur (unchecked-byte (.read is))
                 header
                 (unchecked-inc-int idx)))))))


(defn write-header
  "Header format: number of histories is store as string, followed with a new line (0x0a)
   "
  [^java.io.RandomAccessFile os header & {:keys [fixed size]
                                          :or {fixed true size header-length}}]
  (let [curr ^long (.getFilePointer os)]
    (.seek os 0)
    (if fixed
      (.writeBytes os  (format (format "%%0%dd\n" header-length) header))
      (.writeBytes os  (format "%d\n" header)))
    (when (> curr 0)
      (.seek os curr))))



;; (defn initReconFromFile
;;   ([& {:keys [rows cols] :or {rows 200 cols 200}}]
;;    (let [f "/local/cair/data_4_Ritchie/exp_CTP404/B_1280000_L_1.000000_359/x_0_0.txt"
;;          x (dv 40000)]
;;      (with-open [rdr (io/reader f)]
;;        (loop [lines (line-seq rdr)]
;;          (when-let [[ln & rst] lines]
;;            (println ln)
;;            (recur rst)))))))


#_(defn load-vctr [filename vctr rows cols]
    (assert (= (dim vctr) (* rows cols)))
    (with-open [rdr (io/reader filename)]
      (loop [lines (line-seq rdr)
             offset (long 0)]
        (if-let [[ln & rst] lines]
          (let [str-vals (s/split ln #"\s+")
                n (count str-vals)]
            (assert (= cols n))
            (dotimes [i n]
              (vctr (unchecked-add i offset)  (java.lang.Float/parseFloat (nth str-vals i)))
              #_(vctr (unchecked-add i offset) (unchecked-add i offset) ))
            (recur rst (unchecked-add offset (long rows))))
          vctr))))

(defn load-vctr
  ([filename vctr]
   ;; (println "load-vctr2 v0.2")
   (with-open [rdr (io/reader filename)]
     (let [str-vals (s/split (slurp rdr) #"\s+")]
       (assert (= (dim vctr) (count str-vals)))
       (reduce-kv (fn [acc k v]
                    (acc k (java.lang.Float/parseFloat v))
                    acc)
                  vctr
                  str-vals)))))

(defn load-series [prefix & {:keys [rows cols slices ext iter] :or {rows 200 cols 200 slices 16 ext "txt" iter 0}}]
  ;;Verify files
  (dotimes [i slices]
    (let [fname (format "%s_%d_%d.%s" prefix iter i ext)]
      (if (.exists ^File (File. fname))
        (timbre/info (format "%s ... OK." fname ))
        (java.io.FileNotFoundException. (format "%s not found" fname)))))

  (let [length (* (long rows) (long cols))
        x (dv (* (long length) (long slices)))]
    (loop [offset (long 0)
           i (long 0)]
      (if (< i (long slices))
        (let [fname (format "%s_%d_%d.%s" prefix iter i ext)
              sv (subvector x offset length)]
          (load-vctr fname sv)
          (recur (+ offset  length) (unchecked-inc-int i)))
        x))))



(defn save-x [x prefix & {:keys [id rows cols ext] :or {id 0 rows 200 cols 200 ext "txt"}}]
  (let [fname (format "%s_%d.%s" prefix id ext)
        file ^File (java.io.File. fname)
        z ^float (float 0)]
    (assert (= (dim x) (* (long rows) (long cols))))
    (when-let [d ^File (.getParentFile file)]
      (.mkdirs d))
    (let [fmt ^java.text.DecimalFormat (java.text.DecimalFormat. "0.#######")]
      (with-open [w (clojure.java.io/writer fname :append false)]
        (loop [s-vals (partition rows (map #(if (= % z) "0" (.format fmt %)) x))]
          (when-let [[line & rst] s-vals]
            (.write w (format "%s \n" (clojure.string/join " " line)))
            (recur rst)))))))


(defn save-x2 [x fname & {:keys [rows cols] :or {rows 200 cols 200}}]
  (let [file ^File (java.io.File. ^String fname)
        z ^float (float 0)]
    (assert (= (dim x) (* (long rows) (long cols))))
    (when-let [d ^File (.getParentFile file)]
      (.mkdirs d))
    (let [fmt ^java.text.DecimalFormat (java.text.DecimalFormat. "0.#######")]
      (with-open [w (clojure.java.io/writer fname :append false)]
        (loop [s-vals (partition rows (map #(if (= % z) "0" (.format fmt %)) x))]
          (when-let [[line & rst] s-vals]
            (.write w (format "%s \n" (clojure.string/join " " line)))
            (recur rst)))))))

(defn save-series [x prefix & {:keys [rows cols ext binary filename]
                               :or   {rows 200 cols 200 ext "txt" binary false}}]
  (let [length (* ^long rows ^long cols)
        len ^long (dim x)]
    (if binary
      ;; Write image as a binary file
      ;; start with rows, cols, slices stored as integer, machine format
      ;; followed by x, stored as double array machine format
      (let [slices (/ len length)
            fname (format "%s/%s" prefix (or filename "x.bin"))
            file ^File (java.io.File. fname)]
        (when-let [d ^File (.getParentFile file)]
          (.mkdirs d))
        (with-open [os (clojure.java.io/output-stream fname)]
          (let [rows-buff   (ByteBuffer/allocate 4)
                cols-buff   (ByteBuffer/allocate 4)
                slices-buff (ByteBuffer/allocate 4)
                dbuff       (ByteBuffer/allocate (* len 8))]
            (-> (.order rows-buff ByteOrder/LITTLE_ENDIAN)
                .asIntBuffer
                (.put ^int rows))
            (-> (.order cols-buff ByteOrder/LITTLE_ENDIAN)
                .asIntBuffer
                (.put ^int cols))
            (-> (.order slices-buff ByteOrder/LITTLE_ENDIAN)
                .asIntBuffer
                (.put ^int slices))
            (.write os (.array rows-buff))
            (.write os (.array cols-buff))
            (.write os (.array slices-buff))
            (let [buf ^DoubleBuffer (-> (.order dbuff ByteOrder/LITTLE_ENDIAN)
                                         .asDoubleBuffer)]
              (loop [i (long 0)]
                (if (< i len)
                  (do (.put buf i (x i))
                      (recur (inc i)))
                  (.write os (.array dbuff))))))))
      ;; Write each slice as text image
      (loop [offset (int 0)
             i (int 0)]
        (when (< offset len)
          (save-x (subvector x offset length) prefix :id i :rows rows :cols cols :ext ext)
          (recur (unchecked-add-int offset length) (unchecked-inc-int i)))))))


#_(defn dumpToMatlab [file x history lambda]
  (let [f (if (instance? java.io.File file ))]))
