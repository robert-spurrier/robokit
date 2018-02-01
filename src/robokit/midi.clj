(ns robokit.midi
  (:require [overtone.midi.file :as midi])
  (:import [javax.sound.midi.spi MidiFileWriter]
           [javax.sound.midi
            Sequence
            Track
            MidiEvent
            MidiMessage
            ShortMessage
            MetaMessage
            SysexMessage
            MidiSystem]))

(defn midi-files [dir]
  "Read in a collection of MIDI files and convert into a collection 
   of Clojure maps representing MIDI objects"
  (->> (clojure.java.io/resource dir)
       clojure.java.io/file
       .listFiles
       (filter #(.isFile %))
       (map (comp midi/midi-file str))))

(defmulti midi-message :command)

(defmethod midi-message :note-on [m]
  (ShortMessage. (ShortMessage/NOTE_ON) (:note m) (:velocity m)))

(defmethod midi-message :note-off [m]
  (ShortMessage. (ShortMessage/NOTE_OFF) (:note m) (:velocity m)))

(defn midi-sequence [{:keys [resolution tracks]}]
  (Sequence. (Sequence/PPQ) resolution (count tracks)))

(defn midi-track [sequence]
  (.createTrack sequence))

(defn add-track-event [track message tick]
  (doto track
    (.add (MidiEvent. message (long tick)))))

(defn save-midi-file!
  ([sequence type filename]
   (let [f (java.io.File. filename)]
     (MidiSystem/write sequence type f)))
  ([sequence filename]
   (save-midi-file! sequence 1 filename)))

(defn write-midi-clj! [midi-clj-sequence filename]
  (let [sequence (midi-sequence midi-clj-sequence)
        track (midi-track sequence) ;; assuming one track for now
        events (-> midi-clj-sequence
                   :tracks
                   first
                   :events)]
    (doseq [e events]
      (add-track-event track (midi-message e) (:timestamp e)))
    (save-midi-file! sequence filename)))

;; (def drum-note-vec
;;   [[28 34 35] ;; kick
;;    [36 37 39] ;; snare
;;    [38] ;; clap
;;    [45 53] ;; hi-hat open
;;    [41 43 44 46 51 54] ;; hi-hat closed
;;    [42] ;; claves
;;    [40] ;; cowbell
;;    [47 48 55 56 63]]) ;; crash

;; note-on note frequencies
;; ([28 126]
;;  [34 151]
;;  [35 287]
;;  [36 171]
;;  [37 154]
;;  [38 235]
;;  [39 10]
;;  [40 46]
;;  [41 18]
;;  [42 375]
;;  [43 24]
;;  [44 288]
;;  [45 1]
;;  [46 176]
;;  [47 1]
;;  [48 2]
;;  [51 677]
;;  [53 16]
;;  [54 19]
;;  [55 2]
;;  [56 1]
;;  [63 1])
