(ns wrongtangular.core
  (:require [cljs.core.async :refer [>! <! chan put!]]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(defonce app-state (atom {:queue [], :wrong [], :right []}))

(defn main []
  (om/root
    (fn [app owner]
      (reify
        om/IRender
        (render [_]
          (dom/h1 nil "Hello world!"))))
    app-state
    {:target (. js/document (getElementById "app"))}))
