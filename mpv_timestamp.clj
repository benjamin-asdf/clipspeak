(ns mpv-timestamp
  (:require [babashka.process :as process]))

;; If you are hungry, eat, if you are thirsty, drink, if you tired sleep.
;; If the python script that chat gpt gives you works, that works
;; Such is the unconstraint freedom of the hacker

(defn seconds->timestamp
  [s]
  (let [hours   (int (/ s 3600))
        minutes (int (mod (/ s 60) 60))
        seconds (int (mod s 60))]
    (format "%02d:%02d:%02d" hours minutes seconds)))

(defn curr-timestamp! []
  (let [float-time (read-string (-> (process/sh "python" "mpv-timestamp.py") :out))]
    (seconds->timestamp float-time)))
