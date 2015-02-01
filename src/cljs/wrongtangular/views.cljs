(ns wrongtangular.views
  (:require [clojure.string :as string]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

;; Expects a wrongtangular.core/image-set.
(defn tinder [{:keys [image-set last-action direction]} owner]
  (reify
    om/IRender
    (render [_]
      (let [[previous-image current-image next-image] image-set
            image-title (fn [image]
                          (dom/div #js {:className "image-title"}
                            (str (:id image) ": " (:name image))))
            image-tag (fn [image]
                        (dom/div nil (dom/img #js {:src (:url image)})))]
        (dom/div nil
          ; previous image: will not exist on app load
          (when previous-image
            (let [previous-class
                  (case direction
                    :forward (case last-action
                               :approved "animated fadeOutRight"
                               :rejected "animated fadeOutLeft")
                    :backward "hidden")]
              (dom/div #js {:className previous-class}
                (image-title previous-image)
                (image-tag previous-image))))
          ; current image: always exists if we're rendering this at all
          (dom/div #js {:className (case direction
                                     :forward "animated fadeInUp"
                                     :backward "animated fadeInDown")}
            (image-title current-image)
            (image-tag current-image))
          ; next image: after undo, this is what the user was _about_ to judge
          (when next-image
            (case direction
              :forward
              (let [bg-url (str "url(" (next-image "url") ") no-repeat -9999px -9999px")]
                (dom/div #js {:style {:background bg-url}} ""))
              :backward
              (dom/div #js {:className "animated fadeOutDown"}
                (image-title next-image)
                (image-tag next-image)))))))))

(defn progress [{:keys [app approved-count rejected-count]} owner]
  (reify
    om/IRender
    (render [_]
      (let [index (count (:complete app))
            total (count (mapcat app [:queue :complete]))
            text (str index "/" total ": "
                      approved-count " approved, "
                      rejected-count " rejected")
            percentage (-> index
                         (/ total)
                         (* 100)
                         (Math/round)
                         (str "%"))]
        (dom/div #js {:className "progress"}
          (dom/div #js {:className "text"} text)
          (dom/div #js {:className "total"} "")
          (dom/div #js {:className "complete"
                        :style #js {:width percentage}} ""))))))

(defn loading [owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil "loading..."))))
