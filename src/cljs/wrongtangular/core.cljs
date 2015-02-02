(ns wrongtangular.core
  (:require [wrongtangular.views :as views]
            [wrongtangular.util :as util]
            [cljs.core.async :refer [>! <! chan put!] :as async]
            [clojure.string :as string]
            [goog.events :as events]
            [goog.dom :as gdom]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:import [goog.events EventType]))

(def data-url "data/suspicious_papers.json")

;; Key codes of keys which approve the current image. These are the
;; right-hand home keys, "jkl;".
(def approve-keys #{74 75 76 186}) ; jkl;

;; Key codes of keys which reject the current image. These are the left-hand
;; home keys, "asdf". Also one of the most popular test passwords.
(def reject-keys #{65 83 68 70}) ; asdf

;; Key codes of keys which undo the most recent judgment. Currently only the
;; "u" key.
(def undo-keys #{85}) ; u

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

;; Returns the previous, current, and next images, as a vec. The previous image
;; is held for rapid display on undo, the current one is displayed, and the next
;; one is preloaded.
(defn- image-set [{:keys [queue complete]}]
  [(:item (peek complete))
   (peek queue)
   (peek (pop queue))])

(defn- approved-ids [app]
  (->> (:complete app)
    (filter #(= (:status %) :approved))
    (map #(-> % :item :id))))

(defn- rejected-ids [app]
  (->> (:complete app)
    (filter #(= (:status %) :rejected))
    (map #(-> % :item :id))))

;; Returns the status keyword of the last judged image, either :approved or
;; :rejected.
(defn- last-action [app]
  (:status (peek (:complete app))))

;; Stores the current progress in localStorage as an array of status/id pairs.
(defn- store-in-records [complete]
  (let [dehydrate (fn [entry] [(:status entry) (-> entry :item :id)])]
    (util/store :records (mapv dehydrate complete))))

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

;; Given a result keyword :approved or :rejected and an app state cursor,
;; removes the current item from the queue and adds it to the complete stack as
;; a vector in the form {:status result, :item <item>}.
(defn- advance [result app]
  ; pre-calculate new queue and complete collections so we can both side-effect
  ; with util/store _and_ return the assoc result.
  (let [{:keys [queue complete]} app
        complete (conj complete {:status result, :item (peek queue)})
        queue (pop queue)]
    (store-in-records complete)
    (assoc app :direction :forward, :queue queue, :complete complete)))

(defn- approve [app]
  (om/transact! app (partial advance :approved)))

(defn- reject [app]
  (om/transact! app (partial advance :rejected)))

(defn- undo [app]
  (when-not (empty? (:complete @app))
    (om/transact! app
      (fn [app]
        (let [{:keys [queue complete]} app
              queue (conj queue (:item (peek complete)))
              complete (pop complete)]
          (store-in-records complete)
          (assoc app :direction :backward, :queue queue, :complete complete))))))

(defn- load-initial-data [app]
  (go (let [data (sort-by :id (<! (util/get-json data-url)))]
        (if-let [[queue complete] (restore-from-records data)]
          ; restore records from localStorage without loading JSON
          (om/transact! app
            (fn [app]
              (assoc app
                     :ready? true
                     :queue queue
                     :complete complete)))
          ; load data from JSON, then set it in the app
          (om/transact! app
            (fn [app]
              (assoc app
                     :ready? true
                     :queue (into '() data))))))))

;; Given an input channel of KEYDOWN events, returns an output channel of
;; :approve, :reject, and :undo action keywords. Keystrokes not corresponding
;; to one of those actions will not appear on the output channel.
(defn- keystrokes->actions [in]
  (let [out (chan)]
    (go-loop []
      (let [key-code (.-keyCode (<! in))]
        (cond (approve-keys key-code) (>! out :approve)
              (reject-keys key-code) (>! out :reject)
              (undo-keys key-code) (>! out :undo)))
      (recur))
    out))

(let [body (.-body js/document)
      keystrokes (chan)
      handle-event #(put! keystrokes %)
      inputs (keystrokes->actions keystrokes)]
  (defn- handle-inputs [app]
    ; only handle keyboard events, for now
    (events/listen body goog.events.EventType.KEYDOWN handle-event)
    (go-loop []
      (when-let [input (<! inputs)]
        (condp = input
          :approve (approve app)
          :reject (reject app)
          :undo (undo app))
        (recur))))
  (defn- stop-handling-inputs []
    (events/unlisten body goog.events.EventType.KEYDOWN handle-event)
    (async/close! inputs)))

(defn- setup [app]
  (load-initial-data app)
  (handle-inputs app))

(defn main []
  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (if-not (:ready? app)
            (om/build views/loading app)
            (if-let [current-id (-> app :queue peek :id)]
              (dom/div nil
                (om/build views/progress-bar
                  {:app app
                   :approved-count (count (approved-ids app))
                   :rejected-count (count (rejected-ids app))})
                (dom/div #js {:className "panels"}
                  (dom/div #js {:className "left-panel"}
                    (dom/h1 nil "Wrongtangular!")
                    (dom/div #js {:className "instructions"}
                      (dom/p nil "asdf to reject!")
                      (dom/p nil "jkl; to approve!")
                      (dom/p nil "u to undo!")
                      (dom/p nil "localStorage.clear() to reset!"))
                    (om/build views/progress-text
                      {:app app
                       :approved-count (count (approved-ids app))
                       :rejected-count (count (rejected-ids app))}))
                  (dom/div #js {:className "right-panel"}
                    (om/build views/tinder
                      {:image-set (image-set app)
                       :last-action (last-action app)
                       :direction (:direction app)}
                      {:react-key current-id}))))
              (dom/div nil
                (dom/h1 nil "Processing complete!")
                (dom/p nil "(to start over, run `localStorage.clear()` in the console and refresh.")
                (dom/h2 nil "Approved ids:")
                (dom/textarea #js {:rows 20
                                   :cols 60
                                   :value (string/join ", " (approved-ids app))})
                (dom/h2 nil "Rejected ids:")
                (dom/textarea #js {:rows 20
                                   :cols 60
                                   :value (string/join ", " (rejected-ids app))})))))
        om/IWillMount
        (will-mount [_]
          (setup app))
        om/IWillUnmount
        (will-unmount [_]
          (stop-handling-inputs))))
    app-state
    {:target (. js/document (getElementById "app"))}))
