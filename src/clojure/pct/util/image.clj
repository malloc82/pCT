(ns pct.util.image
  (:use clojure.core)
  (:require [uncomplicate.neanderthal
             [core :refer :all]
             [block :refer [buffer contiguous?]]
             [native :refer :all]
             [auxil :refer :all]
             [math :as math]
             [random :as random]]
            [uncomplicate.fluokitten.core :refer [fmap fmap!]]
            [uncomplicate.neanderthal.internal
             [api :as api]]
            [uncomplicate.neanderthal.internal.host
             [mkl :as mkl]]
            [uncomplicate.neanderthal.random :refer :all])
  (:import javax.imageio.ImageIO
           (java.awt.image BufferedImage Raster WritableRaster)
           (javax.swing JFrame JLabel)
           (java.awt Graphics Dimension Color)
           (uncomplicate.neanderthal.internal.host.buffer_block RealGEMatrix)))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(defmulti imshow (fn [m & _] (class m)))

#_(defmethod imshow mikera.matrixx.Matrix
  [^Matrix m & {:keys [scale color title]}]
  (let [[^Integer row ^Integer col] (m/shape m)
        _m    ^Matrix        (m/clone m)
        image ^BufferedImage (if color
                               (BufferedImage. col row BufferedImage/TYPE_INT_ARGB)
                               (BufferedImage. col row BufferedImage/TYPE_BYTE_GRAY))]
    ;; (println "in imshow matrix")
    (when scale
      (let [_max ^Double (.elementMax _m)
            _min ^Double (.elementMin _m)
            _r   ^Double (- _max _min)]
        (if (not= _r 0.0)
         (m/emap! #(Math/round ^Double (double (* (/ (- % _min) _r) scale))) _m))))
    (if color
      (let [len ^Integer  (* row col)
            arr  ^doubles (m/to-double-array _m nil)
            argb ^ints    (int-array len)]
        (dotimes [i len]
          (aset argb i (Color/HSBtoRGB (/ (- 255.0 (aget arr i)) 360.0) 1.0 1.0)))
        (.setRGB image 0 0 col row argb 0 row))
      (.setPixels ^WritableRaster (.getRaster image) 0 0 col row (m/to-double-array _m nil)))
    (imshow image :title title)))

(defmethod imshow RealGEMatrix
  [^RealGEMatrix m & {:keys [title]}]
  (println "here")
  (let [m    (copy m)
        rows ^int (int (mrows m))
        cols ^int (int (ncols m))
        image ^BufferedImage (BufferedImage. cols rows BufferedImage/TYPE_BYTE_GRAY)
        data (view-vctr m)
        _max ^double (data (imax data))
        _min ^double (data (imin data))
        _r   ^double (- ^double _max ^double _min)]
    (if (not= _r 0.0)
      (fmap! #(math/round (* (/ (- ^double % ^double _min) _r) 255)) data))
    (.setPixels ^WritableRaster (.getRaster image) 0 0 cols rows (double-array data))
    (imshow image :title title)))

(defmethod imshow java.awt.image.BufferedImage
  [^BufferedImage im & {:keys [title width height]}]
  ;; (println "in imshow bufferedimage")
  (let [width  (or width  (+ (.getWidth  im) 15))
        height (or height (+ (.getHeight im) 30))
        canvas (proxy [JLabel] []
                 (paint [g]
                   (.drawImage ^Graphics g im 0 0 this)
                   #_(.drawImage ^Graphics g im 0 0 (.getWidth ^JLabel this) (.getHeight ^JLabel this) this)))]
    {:jframe (doto (JFrame.)
               (.add canvas)
               (.setSize (Dimension. width height))
               (.setTitle ^String (str (or title "no name")))
               (.show)
               (.toFront)
               (.setVisible true))
     :image im}))

(defn showColorRange []
  (let [width 15
        height 255
        image ^BufferedImage (BufferedImage. width height BufferedImage/TYPE_INT_ARGB)
        argb (int-array (* width height))]
    (loop [r 0]
      (when (< r height)
        (loop [c 0]
          (when (< c width)
            (aset argb (+ (* r width) c) (Color/HSBtoRGB (/ r 360.0) 1.0 1.0))
            (recur (unchecked-inc c))))
        (recur (unchecked-inc r))))
    (.setRGB image 0 0 width height argb 0 width)
    (let [canvas (proxy [JLabel] []
                   (paint [g]
                     (.drawImage  ^Graphics g image 0  (- (.getHeight ^JLabel this) height 2) this) ;; leave 2 pixels at the end
                     (.drawString ^Graphics g "255" 20 (+ (- (.getHeight ^JLabel this) height 2) 10))
                     (.drawString ^Graphics g "0"   20 (+ (- (.getHeight ^JLabel this) height 2) 255))))]
      {:jframe (doto (JFrame.)
               (.add canvas)
               (.setSize (Dimension. (+ width 30) (+ height 30)))
               (.show)
               (.toFront)
               (.setVisible true))
     :image image})))
