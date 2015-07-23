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

(defn rejected-ids [app]
  (->> (:complete app)
       (filter #(= (:status %) :rejected))
       (map #(-> % :item :id))
       distinct))

;; Returns the ids of all candidates which have not been rejected. Note that if
;; a candidate has multiple images, it will have one entry per image: even one
;; rejected entry will disqualify the id, but if any entry has been approved,
;; the id will appear in approved-ids even if not all entries have been
;; evaluated.
(defn approved-ids [app]
  (->> (:complete app)
       (filter #(= (:status %) :approved))
       (map #(-> % :item :id))
       (remove (into #{} (rejected-ids app)))
       distinct))

;; Returns the status keyword of the last judged image, either :approved or
;; :rejected.
(defn last-action [app]
  (:status (peek (:complete app))))

;; Stores current queue and complete collections in localStorage.
(defn save! []
  (doseq [k [:queue :complete]]
    (util/store! k (@app-state k))))

;; Returns the most recent :queue and :complete values from localStorage, as a
;; [queue complete] pair. nil if no queue is available in localStorage.
(defn restore-from-records []
  (let [[queue complete] (mapv util/fetch [:queue :complete])]
    (if (nil? queue)
      nil
      ; queue is stored as a JS array in localStorage, which becomes a
      ; Vector when fetched; return it to a List.
      [(apply list queue)
       ; status keywords become string in localStorage; return them
       ; to keywords.
       (mapv (util/transform-map :status keyword) complete)])))
; TODO: avoid both these rehydration steps by using Transit or something?

;; Given a result keyword :approved or :rejected and an app state map, returns
;; the app state with the current item removed from the queue and added it to
;; the complete stack as a vector in the form {:status result, :item <item>}.
;;
;; Has the side effect of updating the :queue and :complete keys in
;; localStorage, hence the bang name.
(defn advance! [result app]
  ; pre-calculate new queue and complete collections so we can both side-effect
  ; with util/store _and_ return the assoc result.
  (let [{:keys [queue complete]} app
        complete (conj complete {:status result, :item (peek queue)})
        queue (pop queue)]
    (save!)
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
          (save!)
          (assoc app :direction :backward, :queue queue, :complete complete))))))

;; Given a seq data structure loaded from candidates.json, generates a list of
;; images to be judged. Each element of the seq must be a hash with :id and
;; :name keys; it must also have either a :url key, whose value is a string, or
;; a :urls key, whose value is a seq of strings.
;;
;; In either case, this function returns one {:id :name :url} hash per url.
;; URLs are assumed unique; ids and names are not.
(defn- data->queue [data]
  (let [url->entry
        (fn [entry url]
          (-> entry (dissoc entry :urls) (assoc :url url)))
        entries->queue
        (fn [queue entry]
          (cond
            ;; place single-url entries directly in the output
            (contains? entry :url) (conj queue entry)
            ;; convert multiple urls to single url entries
            (contains? entry :urls)
            (into queue (map (partial url->entry entry) (:urls entry)))
            ;; return queue unchanged
            :default queue))]
    (apply list (reduce entries->queue [] data))))

;; Loads application data, from localStorage if possible and from the JSON file
;; located at candidates-url if necessary. If the data is loaded from the JSON
;; file, it will be run through data->queue and stashed in localStorage under
;; the :queue key. In either case, updates the app state.
(defn- load-initial-data! []
  (let [[queue complete] (restore-from-records)]
    (if (nil? queue)
      (go (let [data (sort-by :id (<! (util/get-json candidates-url)))
                queue (data->queue data)]
            (om/transact!
              (app-cursor)
              (fn [app]
                (assoc app :ready? true, :queue queue)))))
      (om/transact!
        (app-cursor)
        (fn [app]
          (assoc app :ready? true, :queue queue, :complete (or complete [])))))))
