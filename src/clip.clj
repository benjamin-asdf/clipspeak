(ns clip
  (:require
   [babashka.fs :as fs]
   [babashka.process :as process]
   [clojure.java.shell :as shell]
   ;; [mpv-timestamp :as mpv]
   ))

;; nil from/to means the end or start
(defn ->snip
  [file from to]
  {:file file :from from :to to})

(defn sub-snip [snip from to] (assoc snip :from from :to to))

(defn concat-snips [& snips]
  {:concated-snips (vec snips)})

(defn speed-snip [snip speed-by]
  (assoc snip :speed speed-by))

;; New function to modify volume
(defn volume-snip [snip volume-factor]
  (assoc snip :volume volume-factor))

;; ** 1.3x
;; ffmpeg -i vid1-speed-part-2.mp4 -filter:v "setpts=PTS/1.3" -filter:a "atempo=1.3" vid1-speed-speeded.mp4

;; Modified speed! function to handle volume
(defn
  speed!
  [{:keys [file speed] :as snip}
   out-file]
  (assert
   (and speed (<= 0.5 speed 3.0)))
  (let [speed-cmd (format
                   "ffmpeg -y -i %s -filter:v \"setpts=PTS/%s\" -filter:a \"atempo=%s\" %s"
                   file
                   speed
                   speed
                   out-file)]
    (println speed-cmd)
    (process/shell speed-cmd)
    (merge
     snip
     {:file out-file :speed nil})))

;; Modified cut! function to handle volume
(defn cut!
  [{:keys [file from to volume] :as snip} out-file]
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
        (if volume
          (str "-filter:a \"volume=" volume "\"")
          "-c:a copy")
        out-file])))
    (merge
     snip
     (->snip out-file nil nil))))

;; Volume-only modification function
(defn
  adjust-volume!
  [{:keys [file volume] :as snip}
   out-file]
  (assert
   volume
   "Volume factor must be specified")
  (let [volume-cmd (format
                    "ffmpeg -y -i %s -filter:a \"volume=%s\" -c:v copy %s"
                    file
                    volume
                    out-file)]
    (process/shell volume-cmd)
    (merge
     snip
     {:file out-file :volume nil})))

(defn concat! [snips out-file]
  (let [concat-file-str
        (->> snips
             (map :file)
             (map #(str "file " % "\n"))
             (reduce str))]
    (spit "concat-them" concat-file-str)
    (process/shell "ffmpeg" "-f" "concat" "-safe" "0" "-y" "-i" "concat-them" "-c" "copy" (str out-file)))
  (->snip out-file nil nil))

(def ^:dynamic *dir*)

(defn
  doit-1!
  ([snip]
   (doit-1! snip (fs/file
                  *dir*
                  (str (random-uuid) ".mp4"))))
  ([{:keys [concated-snips]
     :as snip}
    out-file]
   (println "... " snip)
   (let [new-snip (cond
                    (seq concated-snips)
                    (let [concated (concat!
                                    (doall
                                     (map doit-1! concated-snips))
                                    out-file)]
                      (doit-1!
                       (merge
                        concated
                        (dissoc snip :concated-snips))))
                    (or (:from snip) (:to snip))
                    (cut! snip out-file)
                    (:speed snip)
                    (speed! snip out-file)
                    (:volume snip)
                    (adjust-volume! snip out-file)
                    :else
                    snip)]
     (if
         (= new-snip snip)
         snip
         (recur
          new-snip
          (str (random-uuid) ".mp4"))))))

(defn doit! [snip out-file]
  (let [file (fs/absolutize (fs/file out-file))]
    (fs/with-temp-dir [dir {}]
      (binding [*dir* dir]
        (fs/copy (:file (doit-1! snip)) file {:replace-existing true})))))

(comment

  (->
   (concat-snips
    (->snip "/home/benj/video/nevero/nevero.mp4" nil nil)
    (->snip "/home/benj/video/nevero/output.mp4" nil nil))
   (doit! "/tmp/trs-3.mp4"))

  ;; #object[sun.nio.fs.UnixPath 0x3fce499e "/tmp/trs-3.mp4"]


  (->snip
   "/home/benj/video/w/2025-01-30_19-06-09.mkv"
   )

  (->
   (concat-snips
    (->snip "/home/benj/video/counting/2025-02-05_18-45-32.mkv" nil "00:00:59")
    (->snip "/home/benj/video/counting/2025-02-05_18-50-49.mkv" "00:00:10" "00:47:26")
    (->snip "/home/benj/video/counting/2025-02-05_18-50-49.mkv" "00:49:27" nil))
   (doit! "/tmp/trs-3.mp4"))

  ;; #object[sun.nio.fs.UnixPath 0xb8500e3 "/tmp/trs-3.mp4"]





  ;; -----------------
  ;; numbers
  ;;

  (->
   (concat-snips
    (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" nil "00:21:00")
    (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" "00:22:34" "00:30:29")
    (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" "00:30:51" "00:37:26")
    (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" "00:38:22" nil)
    ;; --------------------
    ;; (->snip "/home/benj/video/2025-02-08_15-14-08.mkv" nil nil)
    ;; (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" nil "00:28:54")
    ;; (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:30:00" "00:30:29")
    ;; (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:31:46" "00:32:00")
    ;; (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:32:56" "00:34:05")
    ;; (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:35:37" "00:38:59")
    ;; (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:40:08" "00:43:00")
    ;; (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:46:22" "01:19:56")
    ;; (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "01:25:20" "01:26:13")
    ;; (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "01:32:42" "01:36:26")
    ;; (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "01:44:56" nil)
    )
   (doit! "/tmp/trs-4.mp4"))



  (->
   (concat-snips
    ;; (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" nil "00:21:00")
    ;; (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" "00:22:34" "00:30:29")
    ;; (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" "00:30:51" "00:37:26")
    ;; (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" "00:38:22" nil)
    ;; --------------------
    ;; (->snip "/home/benj/video/2025-02-08_15-14-08.mkv" nil nil)

    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" nil "00:28:54")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:30:00" "00:30:29")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:31:46" "00:32:00")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:32:56" "00:34:05")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:35:37" "00:38:59")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:40:08" "00:43:00")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:46:22" "01:19:56")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "01:25:20" "01:26:13")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "01:32:42" "01:36:26")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "01:44:56" nil))
   (doit! "/tmp/trs-4-3.mp4"))



  (->
   (concat-snips
    ;; (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" nil "00:21:00")
    ;; (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" "00:22:34" "00:30:29")
    ;; (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" "00:30:51" "00:37:26")
    ;; (->snip "/home/benj/video/2025-02-08_08-44-06.mkv" "00:38:22" nil)
    ;; --------------------
    (->snip "/home/benj/video/2025-02-08_15-14-08.mkv" nil nil)

    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" nil "00:28:54")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:30:00" "00:30:29")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:31:46" "00:32:00")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:32:56" "00:34:05")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:35:37" "00:38:59")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:40:08" "00:43:00")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "00:46:22" "01:19:56")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "01:25:20" "01:26:13")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "01:32:42" "01:36:26")
    (->snip "/home/benj/video/2025-02-08_18-54-15.mkv" "01:44:56" nil))
   (doit! "/tmp/trs-4-3.mp4"))

  (->
   (concat-snips
    (->snip "/tmp/trs-4.mp4" nil nil)
    (speed-snip
     (->snip "/home/benj/video/2025-02-08_15-14-08.mkv" "00:00:08" nil)
     1.5)
    (->snip "/tmp/trs-4-3.mp4" nil nil))
   (doit! "/tmp/trs-4-full.mp4"))





















  (doit!
   (->
    (concat-snips
     (->snip "/home/benj/video/2025-02-14_14-01-27.mkv" "00:00:02" "00:03:00")
     (->snip "/home/benj/video/2025-02-14_14-01-27.mkv" "00:03:40" "00:04:54")
     (->snip "/home/benj/video/2025-02-14_14-01-27.mkv" "00:07:39" "00:09:45")
     (->snip "/home/benj/video/2025-02-14_14-01-27.mkv" "00:11:09" nil)
     (->snip "/home/benj/video/2025-02-15_17-10-10.mkv" "00:00:25" "00:01:16")
     (->snip "/home/benj/video/2025-02-15_17-10-10.mkv" "00:01:31" nil)
     (->snip "/home/benj/video/2025-02-14_14-14-46.mkv" nil "00:10:02")
     (->snip "/home/benj/video/2025-02-14_14-14-46.mkv" "00:10:13" "00:15:55")
     (->snip "/home/benj/video/2025-02-14_14-14-46.mkv" "00:17:12" "00:27:17")
     (->snip "/home/benj/video/2025-02-14_14-14-46.mkv" "00:27:21" "00:29:11")

     (->snip "/home/benj/video/2025-02-15_17-37-46.mkv" "00:00:02" "00:01:41")


     (->snip "/home/benj/video/2025-02-15_13-19-18.mkv" "00:00:44" "00:04:05")
     (->snip "/home/benj/video/2025-02-15_13-19-18.mkv" "00:05:44" "00:12:08")
     (->snip "/home/benj/video/2025-02-15_13-19-18.mkv" "00:29:43" "00:33:20")
     (->snip "/home/benj/video/2025-02-15_13-19-18.mkv" "00:38:27" "00:39:44")

     (->snip "/home/benj/video/2025-02-15_14-16-14.mkv" "00:00:28" "00:07:20")
     (->snip "/home/benj/video/2025-02-15_14-16-14.mkv" "00:08:50" "00:18:12")
     (->snip "/home/benj/video/2025-02-15_14-16-14.mkv"
             "00:19:38"
             nil
             ))
    )
   "/tmp/emacs-gemini-quick-4.mkv")

  ;; #object[sun.nio.fs.UnixPath 0x1f6de36a "/tmp/emacs-gemini-quick-4.mkv"]

  (doit!
   (speed-snip
    (->snip "/tmp/emacs-gemini-quick-4.mkv" nil nil)
    1.85)
   "/tmp/egq.mp4")


  ;; #object[sun.nio.fs.UnixPath 0x41aebfed "/tmp/emacs-gemini-quick-3.mkv"]

  (speed-snip
   (->snip "/home/benj/video/2025-02-16_10-27-34.mkv" "00:00:15" "00:08:23")
   1.8)
  ;; need background?


  (doit!
   (speed-snip
    (concat-snips
     (->snip "/home/benj/video/2025-02-16_10-27-34.mkv" "00:08:57" "00:17:18")
     (->snip "/home/benj/video/2025-02-16_10-27-34.mkv" "00:22:16" "00:28:56")
     (->snip "/home/benj/video/2025-02-16_10-27-34.mkv" "00:41:03" nil)
     (->snip "/home/benj/video/2025-02-16_11-12-48.mkv" nil nil))
    1.8)
   "g-stream-2.mkv")



  (doit!
   (concat-snips
    (speed-snip (->snip "/home/benj/video/2025-02-16_10-27-34.mkv" "00:00:15" "00:08:23") 1.8)
    (->snip
     "/home/benj/repos/clipspeak/g-stream-mixed-out.mkv" nil nil)
    )
   "g-stream.mkv")


  ;; #object[sun.nio.fs.UnixPath 0x71d9c24a "/home/benj/repos/clipspeak/g-stream.mkv"]

  ;; /benj/tmp/mixed_audio-gstream-2llllllllllllli.mkv






  )


(comment

  (doit!
   (speed-snip
    (concat-snips
     (->snip "/home/benj/video/2025-02-21_10-58-58.mkv" "00:01:23" "00:05:42")
     (->snip "/home/benj/video/2025-02-21_11-07-14.mkv" "00:00:29" nil))
    1.5
    )
   "revert-buffer2.mp4")



  (doit!
   (speed-snip
    (concat-snips

     (->snip "/home/benj/video/2025-02-25_14-18-33.mkv" "00:00:05" "00:02:03")


     (->snip "/home/benj/video/2025-02-25_14-18-33.mkv"
             ""
             )
     )
    1.5
    )
   "revert-buffer2.mp4")

  (->
   (->snip "/home/benj/video/2025-03-07_09-58-39.mkv" nil nil)
   (speed-snip 2.5)
   (doit! "foo.mp4"))

  ;; #object[sun.nio.fs.UnixPath 0x7f7cb5d8 "/home/benj/repos/clipspeak/foo.mp4"]

  (->
   (concat-snips
    (->snip "/home/benj/repos/clipspeak/foo.mp4" nil "00:03:52")
    (->snip "/home/benj/repos/clipspeak/foo.mp4" "00:04:18" nil))
   (doit! "foo2.mp4"))
  ;; #object[sun.nio.fs.UnixPath 0x7d65e59c "/home/benj/repos/clipspeak/foo2.mp4"]


  ;; ----------------




  (doit!
   (concat-snips
    (->snip "/home/benj/video/2025-04-05_12-45-21.mkv" nil "00:15:54")
    (->snip "/home/benj/video/2025-04-05_12-45-21.mkv" "00:17:07" nil))
   "/home/benj/video/wolfram-3.mp4")




  (doit!
   (concat-snips
    (->snip "/home/benj/video/2025-05-10_09-51-48.mkv" nil "00:06:27")
    (->snip "/home/benj/video/2025-05-10_09-51-48.mkv" "00:06:52" nil)
    (->snip "/home/benj/video/2025-05-10_10-00-15.mkv" nil "00:02:56")
    (->snip "/home/benj/video/2025-05-10_10-00-15.mkv" "00:03:57" nil)
    (->snip "/home/benj/video/2025-05-10_10-06-51.mkv" nil "00:02:37")
    (->snip "/home/benj/video/2025-05-10_10-06-51.mkv" "00:03:30" "00:08:52")
    (->snip "/home/benj/video/2025-05-10_10-06-51.mkv" "00:10:12" "00:11:54")
    (->snip "/home/benj/video/2025-05-10_10-06-51.mkv" "00:12:18" "00:34:52")
    (->snip "/home/benj/video/2025-05-10_10-48-03.mkv" nil "00:06:23")
    (->snip "/home/benj/video/2025-05-10_10-48-03.mkv" "00:08:15" "00:13:38")
    (->snip "/home/benj/video/2025-05-10_10-48-03.mkv" "00:14:42" "00:14:59")
    (->snip "/home/benj/video/2025-05-10_10-48-03.mkv" "00:04:00" "00:11:40")
    (->snip "/home/benj/video/2025-05-10_10-48-03.mkv" "00:12:17" "00:14:16")

    (->snip "/home/benj/video/2025-05-10_10-48-03.mkv" "00:15:02" "00:19:42")
    (->snip "/home/benj/video/2025-05-10_10-48-03.mkv" "00:20:18" nil)
    (->snip "/home/benj/video/2025-05-10_11-05-29.mkv" "00:00:06" nil))
   "/tmp/foo.mp4")


  (doit!
   (concat-snips

    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "01:01:40" "01:02:56")

    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:00:04" "00:00:19")
    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:02:45" "00:03:00")
    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:07:28" "00:07:50")
    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:08:38" "00:10:00")

    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:11:35" "00:12:20")







    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:12:39" "00:13:43")

    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:21:03" "00:23:20")


    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:30:40" "00:33:12")


    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:35:20" "00:41:05")


    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:44:50" "00:47:56")


    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:48:50" "00:49:20")


    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:52:01" "01:11:31")


    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "01:15:30" "01:15:40")

    (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "01:17:05" "01:20:48")


    ;; (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:19:25" "00:19:57")
    ;; (->snip "/home/benj/video/2025-06-20_17-27-12.mkv" "00:19:25" "00:19:57")


    )
   "/tmp/foo.mp4")

#object[sun.nio.fs.UnixPath 0x17de5938 "/tmp/foo.mp4"]















  )
