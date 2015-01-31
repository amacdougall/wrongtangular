(ns wrongtangular.core
  (:require [wrongtangular.views :as views]
            [cljs.core.async :refer [>! <! chan put!] :as async]
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
                    (js->clj))))) ; TODO: use Transit instead?
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
            (om/build views/tinder (next-image app))))
        om/IWillMount
        (will-mount [_]
          (setup app))
        om/IWillUnmount
        (will-unmount [_]
          (stop-handling-inputs))))
    app-state
    {:target (. js/document (getElementById "app"))}))