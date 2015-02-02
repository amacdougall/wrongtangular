(ns wrongtangular.views
  (:require [clojure.string :as string]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]))

(defn- classes [& cs]
  (string/join " " (remove nil? cs)))

;; Expects a wrongtangular.core/image-set.
(defn tinder [{:keys [image-set last-action direction]} owner]
  (reify
    om/IRender
    (render [_]
      (let [[previous-image current-image next-image] image-set
            image (fn [image class-name]
                    (dom/div #js {:className class-name}
                      (dom/p #js {:className "image-title"} (str (:id image) ": " (:name image)))
                      (dom/img #js {:src (:url image)})))]
        (dom/div #js {:className "tinder"}
          ; previous image: will not exist on app load
          (when previous-image
            (let [previous-class (case [direction last-action]
                                   [:forward :approved] "previous animated fadeOutRight"
                                   [:forward :rejected] "previous animated fadeOutLeft"
                                   "hidden")]
              (image previous-image (classes "previous" previous-class))))
          ; current image: always exists if we're rendering this at all
          (image current-image (classes "current" (case direction
                                                    :forward "animated fadeInUp"
                                                    :backward "animated fadeInDown")))
          ; next image: after undo, this is what the user was _about_ to judge
          (when next-image
            (case direction
              :forward
              ; preload next image as an offset background-image
              (let [bg-url (str "url(" (next-image "url") ") no-repeat -9999px -9999px")]
                (dom/div #js {:style {:background bg-url}} ""))
              :backward
                (image next-image "next animated fadeOutDown"))))))))

(defn progress-text [{:keys [app approved-count rejected-count]} owner]
  (reify
    om/IRender
    (render [_]
      (let [index (count (:complete app))
            total (count (mapcat app [:queue :complete]))]
        (dom/div #js {:className "progress-text"}
          (dom/p nil (str index "/" total))
          (dom/p nil (str approved-count " approved"))
          (dom/p nil (str rejected-count " rejected")))))))

(defn progress-bar [{:keys [app approved-count rejected-count]} owner]
  (reify
    om/IRender
    (render [_]
      (let [index (count (:complete app))
            total (count (mapcat app [:queue :complete]))
            percentage (-> index
                         (/ total)
                         (* 100)
                         (Math/round)
                         (str "%"))]
        (dom/div #js {:className "progress-bar"}
          (dom/div #js {:className "total"} "")
          (dom/div #js {:className "complete" :style #js {:width percentage}} ""))))))

(defn loading [owner]
  (reify
    om/IRender
    (render [_]
      (dom/div nil "loading..."))))
