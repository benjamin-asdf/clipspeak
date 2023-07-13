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

(defn concat-snips [& snips] (vec snips))

(defn speed-snip [snip speed-by]
  (assoc snip :speed speed-by))

(defn speed! [{:keys [file from to]} out-file])

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
  ([snip out-file]
   (def snip snip)
   (cond
     (vector? snip)
     (concat! (doall (map doit-1! snip)) out-file)
     (or (:from snip) (:to snip))
     (cut! snip out-file)
     (:speed snip) (speed! snip))))

(defn doit! [snip out-file]
  (doit-1! snip out-file))



(comment
  (doit-1! (-> (->snip "/home/benj/Pictures/bobscheme/vid2.mp4" "00:00:10" "00:00:20")) "foo2.mp4")

  )


(comment

  (let [snip (->snip "/home/benj/Pictures/bobscheme/vid2.mp4" nil nil)
        snips
        (concat-snips
         (sub-snip snip nil "00:00:10")
         (sub-snip snip nil "00:00:20"))]
    (doit! snips (fs/absolutize (fs/file "foo2.mp4"))))

  (mpv/curr-timestamp!)

  )
