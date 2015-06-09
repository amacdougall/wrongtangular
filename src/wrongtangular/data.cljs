(ns wrongtangular.data
  (:require [wrongtangular.util :as util]
            [om.core :as om :include-macros true]
            [cljs.core.async :refer [>! <! chan put!] :as async])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(def candidates-url "data/candidates.json")

;; App state atom. Contains the following keys:
;; :ready? - A Boolean which is true when initial data has loaded.
;; :direction - Either :forward or :backward, based on advance/undo status.
;; :queue - A list of items yet to be judged.
;; :complete - A vector of items which have been judged.
;;
;; Note: The list and complete collections use appropriate data structures.
;; Use peek/pop/conj and enjoy the freedom from implementation details.
(defonce app-state (atom {:ready? false,
                          :direction :forward
                          :queue '()
                          :complete []}))

;; Returns a reference cursor for the app state.
(defn app-cursor []
  (om/ref-cursor (om/root-cursor app-state)))

;; Returns the previous, current, and next images, as a vec. The previous image
;; is held for rapid display on undo, the current one is displayed, and the next
;; one is preloaded.
(defn image-set [{:keys [queue complete]}]
  [(:item (peek complete))
   (peek queue)
   (peek (pop queue))])

(defn approved-ids [app]
  (->> (:complete app)
    (filter #(= (:status %) :approved))
    (map #(-> % :item :id))))

(defn rejected-ids [app]
  (->> (:complete app)
    (filter #(= (:status %) :rejected))
    (map #(-> % :item :id))))

;; Returns the status keyword of the last judged image, either :approved or
;; :rejected.
(defn last-action [app]
  (:status (peek (:complete app))))

;; Stores the current progress in localStorage as an array of status/id pairs.
(defn store-in-records! [complete]
  (let [dehydrate (fn [entry] [(:status entry) (-> entry :item :id)])]
    (util/store! :records (mapv dehydrate complete))))

;; Given a dataset, attempts to produce a queue and a complete list from
;; localStorage data. Returns a [queue complete] pair if successful; nil
;; otherwise.
(defn restore-from-records [data]
  (if-let [records (util/fetch :records)]
    (let [has-id (fn [id] #(= (:id %) id))
          find-by-id (fn [id data] (first (filter (has-id id) data)))
          rehydrate (fn [[status id]]
                      {:status (keyword status)
                       :item (find-by-id id data)})
          complete (mapv rehydrate (sort-by :id records))
          completed-ids (into #{} (map #(-> % :item :id)) complete)
          queue (->> data
                  (remove #(completed-ids (:id %)))
                  (sort-by :id)
                  (into '()))]
      [queue complete])))

;; Given a result keyword :approved or :rejected and an app state map, returns
;; the app state with the current item removed from the queue and added it to
;; the complete stack as a vector in the form {:status result, :item <item>}.
;;
;; Has the side effect of updating local storage, hence the ! name.
(defn advance! [result app]
  ; pre-calculate new queue and complete collections so we can both side-effect
  ; with util/store _and_ return the assoc result.
  (let [{:keys [queue complete]} app
        complete (conj complete {:status result, :item (peek queue)})
        queue (pop queue)]
    (store-in-records! complete)
    (assoc app :direction :forward, :queue queue, :complete complete)))

(defn approve! []
  (om/transact! (app-cursor) (partial advance! :approved)))

(defn reject! []
  (om/transact! (app-cursor) (partial advance! :rejected)))

(defn undo! []
  (when-not (empty? (:complete @app-state))
    (om/transact! (app-cursor)
      (fn [{:keys [queue complete] :as app}]
        (let [queue (conj queue (:item (peek complete)))
              complete (pop complete)]
          (store-in-records! complete)
          (assoc app :direction :backward, :queue queue, :complete complete))))))

;; Given a seq data structure loaded from candidates.json, generates a list of
;; images to be judged. Each element of the seq must be a hash with :id and
;; :name keys; it must also have either a :url key, whose value is a string, or
;; a :urls key, whose value is a seq of strings.
;;
;; In either case, this function returns one {:id :name :url} hash per url.
;; URLs are assumed unique; ids and names are not.
(defn- data->queue [data]
  (let [f (fn [coll entry]
            (cond
              ;; place single-url entries directly in the output
              (contains? entry :url) (conj coll entry)
              ;; convert multiple urls to single url entries
              (contains? entry :urls)
              (vec (concat coll (map #(-> entry (dissoc entry :urls) (assoc :url %))
                                     (:urls entry))))
              ;; return collection unchanged
              :default coll))]
    (into '() (reduce f [] data))))

(defn- load-initial-data! []
  (go (let [data (sort-by :id (<! (util/get-json candidates-url)))]
        (if-let [[queue complete] (restore-from-records data)]
          ; restore records from localStorage without loading JSON
          (om/transact! (app-cursor)
            (fn [app]
              (assoc app
                     :ready? true
                     :queue queue
                     :complete complete)))
          ; load data from JSON, then set it in the app
          (om/transact! (app-cursor)
            (fn [app]
              (assoc app :ready? true, :queue (data->queue data))))))))
