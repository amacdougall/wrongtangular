(ns wrongtangular.views
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(defn tinder [current-image owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil
        (dom/h1 nil "Wrongtangular")
        (dom/div nil
          (dom/img #js {:src (current-image "url")}))
        (dom/div nil (current-image "name"))))))

(defn loading [owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil "loading..."))))
