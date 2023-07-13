(ns clip
  (:require
   [babashka.fs :as fs]
   [babashka.process :as process]
   [clojure.java.shell :as shell]
   [mpv-timestamp :as mpv]))

;; nil from/to means the end or start
(defn ->snip
  [file from to]
  {:file file :from from :to to})

(defn sub-snip [snip from to] (assoc snip :from from :to to))

(defn concat-snips [& snips]
  {:concated-snips (vec snips)})

(defn speed-snip [snip speed-by]
  (assoc snip :speed speed-by))

;; ** 1.3x
;; ffmpeg -i vid1-speed-part-2.mp4 -filter:v "setpts=PTS/1.3" -filter:a "atempo=1.3" vid1-speed-speeded.mp4

(defn speed! [{:keys [file speed]} out-file]
  (assert (and speed (<= 0.5 speed 2.0)))
  (let [speed-cmd (format
                   "ffmpeg -y -i %s -filter:v \"setpts=PTS/%s\" -filter:a \"atempo=%s\" %s"
                   file
                   speed
                   speed
                   out-file)]
    (process/shell speed-cmd)
    (->snip out-file nil nil)))

(defn
  cut!
  [{:keys [file from to]}
   out-file]
  (do
    (process/shell
     (apply
      str
      (interpose
       " "
       ["ffmpeg"
        "-y"
        "-i"
        file
        (when from (str "-ss " from))
        (when to (str "-to " to))
        "-c:v"
        "copy"
        "-c:a"
        "copy"
        out-file])))
    (->snip out-file nil nil)))

(defn concat! [snips out-file]
  (let [concat-file-str
        (->> snips
             (map :file)
             (map #(str "file " % "\n"))
             (reduce str))]
    (spit "concat-them" concat-file-str)
    (process/shell "ffmpeg" "-f" "concat" "-safe" "0" "-y" "-i" "concat-them" "-c" "copy" (str out-file)))
  (->snip out-file nil nil))

(defn doit-1!
  ([snip] (doit-1! snip (str (random-uuid) ".mp4")))
  ([{:keys [concated-snips] :as snip} out-file]
   (cond
     (seq concated-snips)
     (let [concated (concat! (doall (map doit-1! concated-snips)) out-file)]
       (doit-1! (merge concated (dissoc snip :concated-snips))))
     (or (:from snip) (:to snip))
     (cut! snip out-file)
     (:speed snip) (speed! snip out-file)
     :else snip)))

(defn doit! [snip out-file]
  (let [file (fs/absolutize (fs/file out-file))]
    (fs/with-temp-dir [dir {}]
      (fs/copy (:file (doit-1! snip)) file {:replace-existing true}))))

(comment
  (doit-1! (-> (->snip "/home/benj/Pictures/bobscheme/vid2.mp4" "00:00:10" "00:00:20")) "foo2.mp4"))

(comment
  (let [snip (->snip "/home/benj/Pictures/bobscheme/vid2.mp4" nil nil)
        snip
        (->
         (concat-snips
          (sub-snip snip nil "00:00:10")
          (sub-snip snip nil "00:00:20"))
         (speed-snip 1.3))
        out-file "foo.mp4"]
    (doit! snip out-file))

  {:concated-snips [{:file "/home/benj/Pictures/bobscheme/vid2.mp4"
                     :from nil
                     :to "00:00:10"}
                    {:file "/home/benj/Pictures/bobscheme/vid2.mp4"
                     :from nil
                     :to "00:00:20"}]
   :speed 1.3}

  (mpv/curr-timestamp!))
