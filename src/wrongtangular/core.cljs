(ns wrongtangular.core
  (:require [wrongtangular.data :as data]
            [wrongtangular.views :as views]
            [cljs.core.async :refer [>! <! chan put!] :as async]
            [goog.events :as events]
            [om.core :as om :include-macros true])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:import [goog.events EventType]))


(defn main []
  (om/root views/root data/app-state
           {:target (. js/document (getElementById "app"))}))

(main)
