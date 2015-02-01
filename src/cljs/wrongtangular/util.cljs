(ns wrongtangular.util
  (:require [goog.events :as events]
            [cljs.core.async :refer [>! <! chan put!] :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:import [goog.net XhrIo]
           goog.net.EventType))

;; Returns a channel which will contain the decoded JSON data, as a
;; ClojureScript data structure.
(defn get-json [url]
  (let [xhr (XhrIo.)
        out (chan)]
    (events/listen xhr goog.net.EventType.COMPLETE
      (fn [e]
        (put! out (->> (.getResponseText xhr)
                    (.parse js/JSON)
                    (js->clj))))) ; TODO: use Transit instead?
    (. xhr
      (send url "GET"
        #js {"Content-Type" "application/json"}))
    out))

;; Changes string keys to keyword keys in a collection.
(defn keywordify [m]
  (cond
    (map? m) (into {} (for [[k v] m] [(keyword k) (keywordify v)]))
    (coll? m) (vec (map keywordify m))
    :else m))

; localStorage involves a lot of messing around with string decoding, so I
; borrowed these functions from http://adambard.com/blog/a-simple-clojurescript-app/
;; Stores an item in localStorage.
(defn store [k v]
  (.setItem js/localStorage k (js/JSON.stringify (clj->js v))))

;; Retrieves an item from localStorage, or the default value if none is
;; available.
(defn fetch
  ([k] (fetch k nil))
  ([k default]
   (if-let [item (.getItem js/localStorage k)]
     (-> item
       (or (js-obj))
       (js/JSON.parse)
       (js->clj)
       (keywordify))
     (or default nil))))
