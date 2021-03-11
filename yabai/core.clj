(ns core
  (:require
   [cheshire.core :as json]
   [clojure.java.shell :refer [sh]])  )

(defn- format-arg [arg]
  (cond
    (map? arg) (for [[k v] arg] (str (name k) "=" v))
    (boolean? arg) [(if arg "on" "off")]
    (keyword? arg) [(str "--" (name arg))]
    :else [(str arg)]))

(defn- parse-json [s]
  (json/parse-string s true))

(defn exec [& args]
  (let [{:keys [exit out] :as result}
        (apply sh "yabai" "-m" (mapcat format-arg args))]
    (if (zero? exit)
      (json/parse-string out true)
      (ex-info "command failed" (merge result {:args args})))))

(defn- exec* [domain]
  (fn [& args]
    (apply exec domain args)))

(def config (exec* "config"))
(def display (exec* "display"))
(def space (exec* "space"))
(def window (exec* "window"))
(def query (exec* "query"))
(def rule (exec* "rule"))
(def signal (exec* "signal"))
(def event (exec* "event"))

(defn- env [v]
  (System/getenv (str "YABAI_" v)))

(def process-id (env "PROCESS_ID"))
(def recent-process-id (env "RECENT_PROCESS_ID"))
(def window-id (env "WINDOW_ID"))
(def space-id (env "SPACE_ID"))
(def recent-space-id (env "RECENT_SPACE_ID"))
(def display-id (env "DISPLAY_ID"))
(def recent-display-id (env "RECENT_DISPLAY_ID"))
(def button (env "BUTTON"))
(def point (env "POINT"))

(defn pred [k v] (comp #{v} k))

(def visible? (pred :visible 1))
(def app? (partial pred :app))
(def split? (partial pred :split))

(defn group-windows-by-column
  ([]
   (group-windows-by-column
     (->>
       (query :windows :space)
       (filter visible?)
       (remove (split? "none")))))
  ([windows]
   (let [{{display-w :w} :frame} (query :displays :display)]
     (->>
       windows
       (group-by (fn [{{x :x} :frame}]
                   (cond
                     (< x 100) "left"
                     (< x (/ display-w 2)) "middle"
                     :else "right")))))))

(defn set-middle-width [p]
  (let [rr (/ (- 1 p) 2)
        rm (/ p (+ p rr))
        {:strs [left middle right]} (group-windows-by-column)]
    (when (and (not-empty middle) (not-empty right))
      (window (:id (first middle)) :ratio (str "abs:" (- 1 rm)))
      (window (:id (first right)) :ratio (str "abs:" (- 1 rr))))))

(defn stack-on [{on-id :id} windows]
  (doseq [{id :id} windows]
    (window on-id :stack id))
  (window :focus on-id))
