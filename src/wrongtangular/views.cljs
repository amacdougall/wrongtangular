(ns wrongtangular.views
  (:require [wrongtangular.data :as data]
            [wrongtangular.input :as input]
            [clojure.string :as string]
            [om.core :as om :include-macros true]
            [sablono.core :as html :refer-macros [html]]))

(defn- classes [& cs]
  (string/join " " (remove nil? cs)))

;; Displays the previous, current, and next images. Updates with animations as
;; the user processes images. Expects a wrongtangular.core/image-set.
(defn tinder [{:keys [image-set last-action direction]} owner]
  (reify
    om/IDisplayName
    (display-name [_] "Tinder")
    om/IRender
    (render [_]
      (let [[previous-image current-image next-image] image-set
            image-div (fn [image class-name]
                        (html
                          [:div {:class (classes "image" class-name)}
                           [:p {:class "image-title"} (str (:id image) ": " (:title image))]
                           [:img {:src (:url image)}]]))]
        (html
          [:div {:class "tinder"}
           ; previous image: will not exist on app load
           (when previous-image
             (let [previous-class (case [direction last-action]
                                    [:forward :approved] "previous animated fadeOutRight"
                                    [:forward :rejected] "previous animated fadeOutLeft"
                                    "hidden")]
               (image-div previous-image (classes "previous" previous-class))))
           ; current image: always exists if we're rendering this at all
           (image-div current-image (classes "current" (case direction
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
               (image-div next-image "next animated fadeOutDown")))])))))

;; Status text indicating progress through the dataset.
(defn status [{:keys [app approved-count rejected-count]} owner]
  (reify
    om/IDisplayName
    (display-name [_] "Status")
    om/IRender
    (render [_]
      (let [index (count (:complete app))
            total (count (mapcat app [:queue :complete]))]
        (html [:div {:class "status"}
               [:p (str index "/" total " side images")]
               [:p (str approved-count " packages approved")]
               [:p (str rejected-count " packages rejected")]])))))

;; A simple linear progress bar.
(defn progress-bar [{:keys [app approved-count rejected-count]} owner]
  (reify
    om/IDisplayName
    (display-name [_] "ProgressBar")
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

;; Displays a loading screen during the initial load from JSON or localStorage.
(defn loading [app owner]
  (reify
    om/IDisplayName
    (display-name [_] "Loading")
    om/IRender
    (render [_]
      (html [:div {:class "loading"} "Processing data; this may take a few seconds."]))))

;; Displays a progress bar, the previous/current/next images, status text, and help text.
(defn main [{:keys [app current-id]} owner]
  (reify
    om/IDisplayName
    (display-name [_] "Main")
    om/IRender
    (render [_]
      (html
        [:div {:class "main"}
         (om/build progress-bar
           {:app app
            :approved-count (count (data/approved-ids app))
            :rejected-count (count (data/rejected-ids app))})
         [:div {:class "content"}
          [:div {:class "information"}
           [:h1 {:class "title"} "Wrongtangular!"]
           (om/build status
             {:app app
              :approved-count (count (data/approved-ids app))
              :rejected-count (count (data/rejected-ids app))})
           [:div {:class "instructions"}
            [:p "asdf to reject!"]
            [:p "jkl; to approve!"]
            [:p "u to undo!"]
            [:p "localStorage.clear() to reset!"]]]
          (om/build tinder
            {:image-set (data/image-set app)
             :last-action (data/last-action app)
             :direction (:direction app)}
            {:react-key current-id})]]))))

;; Displays the results after all inputs have been processed.
(defn results [app owner]
  (reify
    om/IDisplayName
    (display-name [_] "Results")
    om/IRender
    (render [_]
      (html
        [:div {:class "results"}
         [:h1 "Processing complete!"]
         [:p "To start over, run `localStorage.clear()` in the console and refresh."]
         [:p "Copy and paste these IDs somewhere useful. Better data export is on the to-do list..."]
         [:h2 "Approved ids:"]
         [:textarea {:rows 10
                     :cols 60
                     :value (string/join ", " (data/approved-ids app))}]
         [:h2 nil "Rejected ids:"]
         [:textarea {:rows 10
                     :cols 60
                     :value (string/join ", " (data/rejected-ids app))}]]))))

;; Root component: renders the loading screen, main screen, or results screen,
;; as needed.
(defn root [app owner]
  (reify
    om/IDisplayName
    (display-name [_] "Root")
    om/IRender
    (render [_]
      (if (:ready? app)
        (if-let [current-id (-> app :queue peek :id)]
          (om/build main {:app app, :current-id current-id})
          (om/build results app))
        (om/build loading app)))
    om/IWillMount
    (will-mount [_]
      (input/handle-inputs!))
    om/IWillUnmount
    (will-unmount [_]
      (input/stop-handling-inputs!))))
