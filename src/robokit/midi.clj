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

(defn midi-sequences [dir]
  "Read in a collection of MIDI files and convert into a sequence 
   of Clojure maps representing MIDI objects."
  (->> (clojure.java.io/resource dir)
       clojure.java.io/file
       .listFiles
       (filter #(.isFile %))
       (map (comp midi/midi-file str))))

(defmulti jmidi-message
  "Turns a Clojure MIDI note event into a Java MIDI Message."
  :command)

(defmethod jmidi-message :note-on [m]
  (ShortMessage. (ShortMessage/NOTE_ON) (:note m) (:velocity m)))

(defmethod jmidi-message :note-off [m]
  (ShortMessage. (ShortMessage/NOTE_OFF) (:note m) (:velocity m)))

(defn jmidi-sequence
  "Create a Java MIDI Sequence."
  [resolution tracks]
  (Sequence. (Sequence/PPQ)
             resolution
             tracks))

(defn jmidi-track
  "Create a Java MIDI Track."
  [sequence]
  (.createTrack sequence))

(defn add-track-event [track message tick]
  (doto track
    (.add (MidiEvent. message (long tick)))))

(defn write-jmidi-file!
  "Write a Java MIDI Sequence to the filesystem."
  ([sequence type filename]
   (let [f (java.io.File. filename)]
     (MidiSystem/write sequence type f)))
  ([sequence filename]
   (write-jmidi-file! sequence 1 filename)))

(defn write-midi-sequence!
  "Convert a Clojure MIDI sequence to Java and save to a MIDI file."
  [midi-sequence filename]
  (let [jsequence (jmidi-sequence 96 1)
        jtrack (jmidi-track jsequence)] ;; assuming one track for now
    (doseq [event midi-sequence]
      (add-track-event jtrack (jmidi-message event) (:timestamp event)))
    (write-jmidi-file! jsequence filename)))
