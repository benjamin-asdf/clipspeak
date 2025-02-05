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
   (and speed (<= 0.5 speed 2.0)))
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

(defn
  doit-1!
  ([snip]
   (doit-1!
    snip
    (str (random-uuid) ".mp4")))
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
      (fs/copy (:file (doit-1! snip)) file {:replace-existing true}))))


(comment


  (doit!
   (concat-snips
    (->snip "/home/benj/video/2024-12-02-12-10-37.mkv" nil "00:09:14")
    (->snip "/home/benj/video/2024-12-02-12-22-01.mkv" nil "00:01:45")
    (->snip "/home/benj/video/2024-12-02-12-25-35.mkv" nil "00:10:29")
    (->snip "/home/benj/video/2024-12-02-12-36-29.mkv" nil "00:02:04")
    (->snip "/home/benj/video/2024-12-02-12-39-38.mkv" nil "00:30:22")
    (->snip "/home/benj/video/2024-12-02-18-07-37.mkv" nil "00:26:08")
    (->snip "/home/benj/video/2024-12-02-18-34-08.mkv" nil "00:03:09")
    (->snip "/home/benj/video/2024-12-02-18-38-20.mkv" "00:00:11" "00:42:49")
    )
   "metta-part-3.mp4")



  ;; next part

  (doit!
   (concat-snips
    (->snip "/home/benj/video/2024-12-04-20-04-06.mkv" nil "00:04:07")
    (->snip "/home/benj/video/2024-12-05-09-36-02.mkv" nil "00:04:49")

    (->snip "/home/benj/video/2024-12-05-09-36-02.mkv" "00:14:33" "00:36:57")

    (->snip "/home/benj/video/2024-12-05-09-36-02.mkv" "00:38:03" "00:46:38")


    (->snip "/home/benj/video/2024-12-05-10-26-59.mkv" nil "00:02:21")
    (->snip "/home/benj/video/2024-12-05-10-26-59.mkv" "00:07:16" "00:24:40")
    (->snip "/home/benj/video/2024-12-05-10-26-59.mkv" "00:38:55" "00:46:06")

    (->snip "/home/benj/video/2024-12-05-10-26-59.mkv" "00:49:40" "00:51:29")


    (->snip "/home/benj/video/2024-12-05-10-26-59.mkv" "00:53:28" "00:59:44")

    (->snip "/home/benj/video/2024-12-05-13-44-24.mkv" nil "00:47:57")
    (->snip "/home/benj/video/2024-12-05-13-44-24.mkv" "00:51:03" "00:56:21")

    (->snip "/home/benj/video/2024-12-05-13-44-24.mkv" "00:59:08" nil))
   "aoc-day4.mp4")


  (doit!
   (concat-snips
    (->snip "/home/benj/repos/clipspeak/day6part1.mp4" "00:53:25" "00:53:51")
    (->snip "/home/benj/repos/clipspeak/day6part1.mp4" nil nil))
   "aoc-day6p1.mp4"
   )


  ;; non deterministic results


  (doit!
   (concat-snips
    (->snip "/home/benj/video/2024-12-07-12-14-11.mkv" "00:12:32" nil)
    (->snip "/home/benj/video/2024-12-07-13-06-58.mkv" nil "00:25:31"))
   "metta-nond.mp4")


  ;; #object[sun.nio.fs.UnixPath 0x5705e310 "/home/benj/repos/clipspeak/metta-nond.mp4"]


  ;; sound:
  ;; https://soundcloud.com/mariuszpierog


  (doit!
   (concat-snips
    (->snip "/home/benj/video/2024-12-08-10-18-16.mkv" nil "00:02:33")
    (->snip "/home/benj/video/2024-12-08-10-18-16.mkv" "00:03:30" "00:07:03")
    (->snip "/home/benj/video/2024-12-08-10-18-16.mkv" "00:10:18" "00:20:45")
    (->snip "/home/benj/video/2024-12-08-10-18-16.mkv" "00:23:46" "00:31:28")
    (->snip "/home/benj/video/2024-12-08-10-59-35.mkv" "00:01:05" "00:36:49")
    (->snip "/home/benj/video/2024-12-08-10-59-35.mkv" "00:44:18" nil))
   "metta-control-flow.mp4")





  (doit!
   (->snip "/home/benj/video/2024-12-08-17-00-56.mkv" "00:00:05" nil)

   "example.mp4")

  ;; ffmpeg -i video.mp4 -i audio1.wav -i audio2.wav -i audio3.wav \
  ;; -map 0:v -map 0:a -map 1:a -map 2:a -map 3:a \
  ;; -c:v copy -c:a copy \
  ;; output.mp4

  ;; ffmpeg -i originalfile.mov -c:v copy -c:a aac -b:a 160k -ac 2 -filter_complex amerge=inputs=2 output.mp4


  ;;
  ;; ffmpeg -i /home/benj/video/2024-12-08-17-47-15.mkv -c:v copy -c:a aac -b:a 160k -/home/benj/video/2024-12-08-18-27-46.mkv2 -filter_complex amerge=inputs=2 output2.mp4

  (doit!
   (concat-snips
    (->snip "/home/benj/video/output2.mp4" nil "00:29:55")
    (->snip "/home/benj/repos/clipspeak/metta-python-2.mkv" nil "00:30:51"))
   "metta-python-p1.mp4")

  ;; #object[sun.nio.fs.UnixPath 0x314ee71f "/home/benj/repos/clipspeak/metta-python-p1.mp4"]

  (doit!
   (concat-snips
    (->snip "/home/benj/repos/clipspeak/parser2.mkv" nil "00:09:48")
    (->snip "/home/benj/repos/clipspeak/parsing-and-interpretation.mkv" nil "00:32:40")
    (->snip "/home/benj/repos/clipspeak/parsing-and-interpretation.mkv" "00:44:47" "01:17:18")
    (->snip "/home/benj/repos/clipspeak/parsing-and-interpretation.mkv" "01:25:00" nil))
   "metta-python-p2.mp4")



  (->snip "/home/benj/notes/output.wav")


  ;; #object[sun.nio.fs.UnixPath 0x35aba98f "/home/benj/repos/clipspeak/day-7-aoc.mp4"]












  (doit!
   (concat-snips
    (->snip "/home/benj/video/2024-12-14-20-56-52.mkv"
            "00:00:55"
            "00:51:05")
    (->snip "/tmp/0001-74303.mp4" nil nil))
   "aoc-day14.mp4")


  ;; #object[sun.nio.fs.UnixPath 0x6d2edd0 "/home/benj/repos/clipspeak/aoc-day14.mp4"]


  ;; # Generate left channel tone (400 Hz)
  ;; ffmpeg -f lavfi -i "sine=frequency=200:duration=300" -c:a pcm_s16le left_tone.wav

  ;; # Generate right channel tone (440 Hz - creating a 40 Hz gamma beat)
  ;; ffmpeg -f lavfi -i "sine=frequency=240:duration=300" -c:a pcm_s16le right_tone.wav

  ;; # Combine the tones into stereo binaural beat

  ;; ffmpeg -i left_tone.wav -i right_tone.wav -filter_complex "[0:a][1:a]join=inputs=2:channel_layout=stereo[a]" -map "[a]" binaural_beat.wav


  ;; # Lower the volume of the binaural beat (optional but recommended)
  ;; ffmpeg -i binaural_beat.wav -filter:a "volume=0.3" binaural_beat_quiet.wav

  ;; # Mix the binaural beat with your existing audio file
  ;; # Replace "your_audio.mp3" with your actual audio file

  ;; ffmpeg -i your_audio.mp3 -i binaural_beat_quiet.wav -filter_complex \
  ;; "[0:a][1:a]amix=inputs=2:duration=first:weights=1 0.5[a]" \
  ;; -map "[a]" output_with_binaural.mp3

  ;; # Clean up temporary files
  ;; rm left_tone.wav right_tone.wav binaural_beat.wav binaural_beat_quiet.wav


  ;; ffmpeg  -i input.mp4 -stream_loop -1 -i input.mp3 -shortest -map 0:v:0 -map 1:a:0 -y out.mp4



  (->
   (->snip "/home/benj/video/2025-01-11-10-08-54.mkv" nil nil)
   (speed-snip 1.5)
   (volume-snip 1.5)
   (doit! "trs1.mp4"))

  (->
   (concat-snips
    (->snip "/home/benj/video/2025-01-11-18-00-44.mkv" nil "00:02:50")
    (->snip "/home/benj/video/2025-01-11-18-00-44.mkv" "00:08:34" nil))
   (doit! "type-pi-pt2.mp4"))








  (->

   (speed-snip 2)
   (doit! "learning-pi-1.mp4"))

  (->

   (concat-snips
    (->snip "/home/benj/video/output.mp4" nil "00:03:25")

    (->snip "/home/benj/video/output.mp4" "00:05:52" "00:05:55")
    (->snip "/home/benj/video/output.mp4" "00:06:22" "00:14:57")
    (->snip "/home/benj/video/output.mp4" "00:17:27" "00:23:20")

    (->snip "/home/benj/video/output.mp4" "00:23:20" "00:23:35")
    (->snip "/home/benj/video/output.mp4" "00:25:46" "00:29:31")
    (->snip "/home/benj/video/output.mp4" "00:31:26" "00:40:49")
    (->snip "/home/benj/video/output.mp4" "00:43:40" "00:44:10"))
   (speed-snip 2)
   (doit! "overtone-1.mp4"))

  (->
   (concat-snips
    (->snip "/home/benj/video/output.mp4"
            "00:44:10"
            "00:59:29")
    (->snip "/home/benj/video/output.mp4" "01:00:12" nil))
   (doit! "overtone-2.mp4"))



  (->
   (concat-snips
    (->snip "/home/benj/trs-output.mp4" nil "00:46:31")
    )
   (doit! "trs-2.mp4"
          ))



  )
