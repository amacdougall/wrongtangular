(ns wrongtangular.input
  (:require [wrongtangular.data :as data]
            [cljs.core.async :refer [>! <! chan put!] :as async]
            [goog.events :as events])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:import [goog.events EventType]))

;; Key codes of keys which approve the current image. These are the
;; right-hand home keys, "jkl;".
(def approve-keys #{74 75 76 186}) ; jkl;

;; Key codes of keys which reject the current image. These are the left-hand
;; home keys, "asdf". Also one of the most popular test passwords.
(def reject-keys #{65 83 68 70}) ; asdf

;; Key codes of keys which undo the most recent judgment. Currently only the
;; "u" key.
(def undo-keys #{85}) ; u

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

  ;; Begin handling inputs by calling the approve, reject, and undo functions
  ;; from wrongtangular.data, as appropriate.
  (defn handle-inputs! []
    ; TODO: handle swipes
    (events/listen body goog.events.EventType.KEYDOWN handle-event)
    (go-loop []
      (when-let [input (<! inputs)]
        (condp = input
          :approve (data/approve!)
          :reject (data/reject!)
          :undo (data/undo!))
        (recur))))

  ;; Stop handling inputs.
  (defn stop-handling-inputs! []
    (events/unlisten body goog.events.EventType.KEYDOWN handle-event)
    (async/close! inputs)))
