(ns wrongtangular.core
  (:require [cljs.core.async :refer [>! <! chan put!] :as async]
            [goog.events :as events]
            [goog.dom :as gdom]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true])
  (:import [goog.net XhrIo]
           goog.net.EventType
           [goog.events EventType])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

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

;; Returns a channel which will contain the decoded JSON data, as a
;; ClojureScript data structure.
(defn get-json [url]
  (let [xhr (XhrIo.)
        out (chan)]
    (events/listen xhr goog.net.EventType.COMPLETE
      (fn [e]
        (put! out (->> (.getResponseText xhr)
                    (.parse js/JSON)
                    (js->clj)))))
    (. xhr
      (send url "GET"
        #js {"Content-Type" "application/json"}))
    out))

(defonce app-state (atom {:ready? false,
                          :queue [],
                          :rejected [],
                          :approved []}))

; Returns the next image to be examined, from the app-state queue.
(defn- next-image [app]
  (first (:queue app)))

(defn- approve [app]
  (.log js/console "image approved!"))

(defn- reject [app]
  (.log js/console "image rejected!"))

(defn- undo [app]
  (.log js/console "last action undone!"))

(defn- load-initial-data [app]
  (go (let [data (<! (get-json data-url))]
        (.log js/console (pr-str data)) ; why is this happening twice, then?
        (om/transact! app #(assoc % :ready? true, :queue data)))))

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

(defn- handle-inputs [app]
  ; only handle keyboard events, for now
  (let [body (.-body js/document)
        keystrokes (chan)
        inputs (keystrokes->actions keystrokes)]
    (events/listen body goog.events.EventType.KEYDOWN #(put! keystrokes %))
    (go-loop []
      (let [input (<! inputs)]
        (condp = input
          :approve (approve app)
          :reject (reject app)
          :undo (undo app)))
      (recur))))

(defn- setup [app]
  (load-initial-data app)
  (handle-inputs app))

;; View shown when 
(defn loading-view [owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil "loading..."))))

(defn chooser-view [app owner]
  (reify
    om/IRender
    (render [_]
      (let [current-image (next-image app)]
        (dom/div #js {:className "centered"}
          (dom/h1 nil "Wrongtangular")
          (dom/div nil
            (dom/img #js {:src (current-image "url")}))
          (dom/div nil (current-image "name")))))))

(defn main []
  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (if-not (:ready? app)
            (om/build loading-view app)
            (om/build chooser-view app)))
        om/IDidMount
        (did-mount [_]
          (.log js/console "main/did-mount occurred; ideally this happens once")
          (setup app))))
    app-state
    {:target (. js/document (getElementById "app"))}))
