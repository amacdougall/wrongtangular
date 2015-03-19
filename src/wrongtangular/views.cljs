(ns wrongtangular.views
  (:require [wrongtangular.data :as data]
            [wrongtangular.input :as input]
            [clojure.string :as string]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn- classes [& cs]
  (string/join " " (remove nil? cs)))

;; TODO: doc comment

;; Expects a wrongtangular.core/image-set.
(defn tinder [{:keys [image-set last-action direction]} owner]
  (reify
    om/IRender
    (render [_]
      (let [[previous-image current-image next-image] image-set
            image (fn [url class-name]
                    (html
                      [:div {:class class-name}
                       [:p {:class "image-title"} (str (:id url) ": " (:name url))]
                       [:img {:src (:url url)}]]))]
        (html
          [:div {:class "tinder"}
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
                 [:div {:style {:background bg-url}} ""])
               :backward
               (image next-image "next animated fadeOutDown")))])))))

(defn progress-text [{:keys [app approved-count rejected-count]} owner]
  (reify
    om/IRender
    (render [_]
      (let [index (count (:complete app))
            total (count (mapcat app [:queue :complete]))]
        (html [:div {:class "progress-text"}
               [:p (str index "/" total)]
               [:p (str approved-count " approved")]
               [:p (str rejected-count " rejected")]])))))

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
        (html [:div {:class "progress-bar"}
               [:div {:class "total"} ""]
               [:div {:class "complete"
                      :style {:width percentage}} ""]])))))

(defn loading [owner]
  (reify
    om/IRender
    (render [_]
      (html [:div nil "loading..."]))))

(defn main [{:keys [app current-id]} owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div
         (om/build progress-bar
           {:app app
            :approved-count (count (data/approved-ids app))
            :rejected-count (count (data/rejected-ids app))})
         [:div {:class "panels"}
          [:div {:class "left-panel"}
           [:h1 "Wrongtangular!"]
           [:div {:class "instructions"}
            [:p "asdf to reject!"]
            [:p "jkl; to approve!"]
            [:p "u to undo!"]
            [:p "localStorage.clear() to reset!"]]
           (om/build progress-text
             {:app app
              :approved-count (count (data/approved-ids app))
              :rejected-count (count (data/rejected-ids app))})]
          [:div {:class "right-panel"}
           (om/build tinder
             {:image-set (data/image-set app)
              :last-action (data/last-action app)
              :direction (:direction app)}
             {:react-key current-id})]]]))))

(defn results [app owner]
  (reify
    om/IRender
    (render [_]
      (html
        [:div
         [:h1 "Processing complete!"]
         [:p "(to start over, run `localStorage.clear()` in the console and refresh."]
         [:h2 "Approved ids:"]
         [:textarea {:rows 20
                     :cols 60
                     :value (string/join ", " (data/approved-ids app))}]
         [:h2 nil "Rejected ids:"]
         [:textarea {:rows 20
                     :cols 60
                     :value (string/join ", " (data/rejected-ids app))}]]))))

;; TODO: doc comment
(defn root [app owner]
  (reify
    om/IDisplayName
    (display-name [_] "Root")
    om/IRender
    (render [_]
      (if-not (:ready? app)
        (om/build loading app)
        (if-let [current-id (-> app :queue peek :id)]
          (om/build main {:app app, :current-id current-id})
          (om/build results app))))
    om/IWillMount
    (will-mount [_]
      (data/load-initial-data!)
      (input/handle-inputs!))
    om/IWillUnmount
    (will-unmount [_]
      (input/stop-handling-inputs!))))
