(ns pct.util.buffer
  (:require [clojure.core.async.impl.protocols :as impl])
  (:import [java.util LinkedList]))

(set! *warn-on-reflection* true)

(defprotocol IInspect
  (inspect [this])
  (clear!  [this]))

(deftype TransparentBuffer [buf]
  impl/Buffer
  (full? [this] false)
  (remove! [this]
    (let [e (.getLast ^LinkedList @buf)]
      (swap! buf (fn [b] (.removeLast ^LinkedList b) b))
      e))
  (add!* [this itm]
    (swap! buf (fn [b] (.addFirst ^LinkedList b itm) b)))
  (close-buf! [this])

  clojure.lang.Counted
  (count [this] (.size ^LinkedList @buf))

  IInspect
  (inspect [this] (vec @buf))
  (clear!  [this] (reset! buf (LinkedList.))))

(defn transparent-buffer []
  (TransparentBuffer. (atom (LinkedList.))))

(deftype InfiniteBuffer [^LinkedList buf]
  impl/Buffer
  (full? [this] false)
  (remove! [this]
    (.removeLast buf))
  (add!* [this itm]
    (.addFirst buf itm)
    this)
  (close-buf! [this])
  clojure.lang.Counted
  (count [this]
    (.size buf)))

(defn infinite-buffer []
  (InfiniteBuffer. (LinkedList.)))
