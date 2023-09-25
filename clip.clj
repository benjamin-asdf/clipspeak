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
  (let [
        snip
        (->
         (concat-snips
          (->snip "/home/benj/Pictures/screen-230826-1935-09.mp4" nil "00:00:11")
          (->snip "/home/benj/Pictures/screen-230826-1936-38.mp4" nil "00:08:58")
          (->snip "/home/benj/Pictures/screen-230826-2018-21.mp4" nil "00:03:11")
          (->snip "/home/benj/Pictures/screen-230826-2018-21.mp4" "00:04:45" "00:08:08")
          (->snip "/home/benj/Pictures/screen-230826-2018-21.mp4" "00:20:43" nil))
         (speed-snip 1.8))
        out-file "foo.mp4"]
    (doit! snip out-file))

  (let [
        snip
        (->
         (concat-snips
          (->snip "/storage-disk/phone-audio/202303/20220905_210542.m4a"  nil "00:02:00"))
         (speed-snip 1.8))
        out-file "foo.mp4"]
    (doit! snip out-file))

  (let [snip
        (->
         (concat-snips
          (->snip "/home/benj/Pictures/screen-230909-1741-15.mp4" nil "00:02:00")
          (->snip "/home/benj/Pictures/screen-230909-1741-15.mp4" "00:04:25" "00:12:00")
          (->snip "/home/benj/Pictures/screen-230909-1741-15.mp4" "00:12:47" "00:13:34")
          (->snip "/home/benj/Pictures/screen-230909-1741-15.mp4" "00:23:38" "00:25:27")
          (->snip "/home/benj/Pictures/screen-230909-1741-15.mp4" "00:33:40" "00:37:00")

          (->snip "/home/benj/Pictures/screen-230909-1938-07.mp4" nil "00:03:21")
          (->snip "/home/benj/Pictures/screen-230909-1945-50.mp4" nil "00:03:55")
          (->snip "/home/benj/Pictures/screen-230909-1945-50.mp4" "00:04:22" "00:11:47")
          (->snip "/home/benj/Pictures/screen-230909-1945-50.mp4" "00:12:52" "00:14:57")
          (->snip "/home/benj/Pictures/screen-230909-1945-50.mp4" "00:30:07" "00:48:46")
          (->snip "/home/benj/Pictures/screen-230909-1945-50.mp4" "00:50:00" "00:53:30")
          (->snip "/home/benj/Pictures/screen-230909-1945-50.mp4" "01:00:55" "01:06:38")
          (->snip "/home/benj/Pictures/screen-230909-1945-50.mp4" "01:08:27" "01:42:30")
          (->snip "/home/benj/Pictures/screen-230909-1945-50.mp4" "01:44:30" "01:51:46")
          (->snip "/home/benj/Pictures/screen-230909-1945-50.mp4" "01:54:26" "02:14:30")
          (->snip "/home/benj/Pictures/screen-230909-1945-50.mp4" "02:16:12" "02:22:43")
          (->snip "/home/benj/Pictures/screen-230909-1945-50.mp4" "02:25:10" nil)
          (->snip "/home/benj/Pictures/screen-230909-2216-19.mp4" nil "00:03:39")
          (->snip "/home/benj/Pictures/screen-230909-2216-19.mp4" "00:16:10" "00:16:30")
          (->snip "/home/benj/Pictures/screen-230909-2216-19.mp4" "00:19:45" "00:21:30")
          (->snip "/home/benj/Pictures/screen-230909-2216-19.mp4" "00:28:13" "00:40:00")
          (->snip "/home/benj/Pictures/screen-230909-2216-19.mp4" "00:51:52" "00:59:08")
          (->snip "/home/benj/Pictures/screen-230909-2216-19.mp4" "01:03:18" nil))
         ;; (speed-snip 1.8)
         )
        out-file "brownians.mp4"]
    (doit! snip out-file))


#object[sun.nio.fs.UnixPath 0x463554d2 "/home/benj/repos/clipspeak/brownians.mp4"]







  (mpv/curr-timestamp!))
