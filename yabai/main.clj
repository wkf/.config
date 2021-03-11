(ns main
  (:require [core :as yabai]))

(defmulti run (fn [command & args] command))

(def config
  {"mouse_follow_focus"           false
   "focus_follows_mouse"          false
   "window_placement"             "first_child"
   "window_topmost"               false
   "window_shadow"                true
   "window_opacity"               false
   "split_ratio"                  0.50
   "auto_balance"                 false
   "mouse_modifier"               "cmd"
   "mouse_action1"                "move"
   "mouse_action2"                "resize"
   "mouse_drop_action"            "stack"
   "layout"                       "bsp"
   "top_padding"                  30
   "bottom_padding"               30
   "left_padding"                 20
   "right_padding"                20
   "window_gap"                   20})

(def rules
  [{:app "Finder" :manage "off"}
   {:app "System Preferences" :manage "off"}
   {:app "System Information" :manage "off"}
   {:app "Harvest" :manage "off"}])

(def signals
  [
   #_{:label "restack"
      :event "window_focused"
      :action "~/.config/yabai/main.sh signal restack"}])

(defn set-config! []
  (doseq [[k v] config]
    (yabai/config k v)))

(defn set-rules! []
  (doseq [r rules]
    (yabai/rule :add r)))

(defn set-signals! []
  (doseq [s signals]
    (yabai/signal :add s)))

(defn set-layout! []
  (let [windows (group-by :app (yabai/query :windows :space))
        [slack] (get windows "Slack")
        [emacs] (get windows "Emacs")
        [left-chrome
         right-chrome] (get windows "Google Chrome")]
    (yabai/window (:id right-chrome) :swap "largest")
    (yabai/window (:id right-chrome) :insert "west")
    (yabai/window (:id emacs) :warp (:id right-chrome))
    (yabai/window (:id emacs) :insert "west")
    (yabai/window (:id left-chrome) :warp (:id emacs))
    (yabai/window (:id left-chrome) :insert "south")
    (yabai/window (:id slack) :warp (:id left-chrome))

    (->> ["Bear"
          "Things"
          "Discord"
          "Messages"]
      (mapcat #(get windows %))
      (yabai/stack-on slack))

    (->> ["Jira"
          "Airmail"
          "Fantastical"]
      (mapcat #(get windows %))
      (yabai/stack-on left-chrome))

    (->> ["Spotify"]
      (mapcat #(get windows %))
      (yabai/stack-on right-chrome))

    (yabai/space :balance)

    (yabai/set-middle-width 0.4)

    (yabai/window (:id slack) :ratio "abs:0.60")

    (yabai/window :focus (:id emacs))))

(defmethod run "config" [_ & args]
  (set-config!)
  (set-rules!)
  (set-layout!))

(defmethod run "signal" [_ name & args]
  (condp = name
    "restack"
    (let [{:keys [stack-index]} (yabai/query :windows :window)]
      (when (> stack-index 1)
        (yabai/window :layer 1)))
    "unknown signal"))

(defmulti select (fn [app & args] app))

(defmethod select :default [app & columns]
  (if (empty? columns)
    (when-let [{:keys [id]} (->>
                              (yabai/query :windows :space)
                              (filter (yabai/app? app))
                              first)]
      (yabai/window :focus id))
    (let [windows (->>
                    (yabai/query :windows :space)
                    (filter (yabai/app? app))
                    yabai/group-windows-by-column)]
      (when-let [{:keys [id]}
                 (some
                   #(first (not-empty (get windows %)))
                   columns)]
        (yabai/window :focus id)))))

(comment
  (select "Google Chrome" "left" "middle")
  (select "Messages"))

(defmethod run "select" [_ & args]
  (apply select args))

(when (not-empty *command-line-args*)
  (apply run *command-line-args*))
