(ns robokit.train
  (:require [jutsu.ai.core :as ai]
            [cprop.core :refer [load-config]])
  (:import [java.io File]
           [org.deeplearning4j.datasets.datavec SequenceRecordReaderDataSetIterator]
           [org.datavec.api.split FileSplit]
           [org.datavec.api.records.reader.impl.csv CSVSequenceRecordReader]))

(defn update-encs
  "Flip the drum hit and velocity bits for the current event."
  [drum-vectors timestamp drum-index]
  (assoc-in drum-vectors [timestamp drum-index] 1))

(defn encode
  "Extract MIDI note event information and use it to update the initialized drum-vectors.
   We are standardizing to a note length of 12 ticks, so we update 12 vectors per event."
  [midi-mappings-input drum-vectors event]
  (let [note (:note event)
        time (:timestamp event)
        drum-index (get midi-mappings-input note)]
    (loop [timestamp-range (range time (+ time 12))
           result drum-vectors]
      (if-let [current-timestamp (first timestamp-range)]
        (recur (rest timestamp-range)
               (update-encs result current-timestamp drum-index))
        result))))

(defn clip-tick-length
  "Fetches the max tick number for a midi clip.
   Adds 12 to ensure there is no note overflow."
  [midi-sequence]
  (+ 12 (-> midi-sequence :tracks first :events last :timestamp)))

(defn empty-tick-vector
  "Return a zeroed one-hot vector representing the drums struck on one tick of
   the MIDI clock."
  []
  (vec (repeat 8 0)))

(defn init-vectors
  "Given a single MIDI clip, creates a zeroed out one-hot vector for each tick
   in the clip."
  [midi-sequence]
  (let [max-ticks (clip-tick-length midi-sequence)]
    (vec (repeatedly max-ticks empty-tick-vector))))

(defn note-on-events
  "Get just the note on events from the MIDI clip.
   For our use case (drums) we are standardizing note length, so other commands can be discarded."
  [midi-sequence]
  (filter
   #(= :note-on (:command %))
   (-> midi-sequence :tracks first :events)))

(defn encoded-clip
  "Encodes a MIDI drum track into a sequence of one-hot vectors."
  [midi-mappings-input midi-sequence]
  (reduce (partial encode midi-mappings-input)
          (init-vectors midi-sequence)
          (note-on-events midi-sequence)))

(defn encoded-string
  "Convert a feature feactor and a label vector into something readable by DL4j
   e.g. [[0 1 0 0 0 0 1 0] [0 1 0 0 0 0 1 0]] -> 0,1,0,0,0,0,1,0,66"
  [[fv lv]]
  (str (clojure.string/join "," fv)
       ","
       (Integer/parseInt (reduce str lv) 2)))

(defn write-training-data!
  "Given a filename, a collection of MIDI clojure maps representing each drum clip,
   convert each drum clip to vectors and write them to files for training in DL4j."
  [filename midi-sequences]
  (let [midi-mappings-input (-> (load-config)
                                :midi-mappings
                                :input)]
   (with-open [w (clojure.java.io/writer filename)]
     (doseq [line (->> midi-sequences
                       (mapcat (partial encoded-clip midi-mappings-input))
                       (partition 2 1)
                       (map encoded-string))]
       (.write w line)
       (.newLine w)))))

;; For future reference:
;; 'you can also RecordReaderMultiDataSetIterator (which is a bit more powerful)
;; and wrap with MultiDataSetWrapperIterator if required'
(defn sequence-classification-csv-iterator
  "Use for Recurrent Neural Nets."
  [filename batch-size label-index num-possible-labels]
  (let [path (File. filename)
        rr (CSVSequenceRecordReader. 0 ",")]
    (.initialize rr (FileSplit. path))
    (SequenceRecordReaderDataSetIterator. rr batch-size num-possible-labels label-index)))

(defn train-net! [net epochs dataset-iterator]
  (doseq [n [range 0 epochs]]
    (println (str "Epoch " n))
    (.reset dataset-iterator)
    (.fit net dataset-iterator))
  net)

(defn robokit [network-config]
  "Initialize the LSTM-RNN."
  (-> network-config      
      ai/network
      ai/initialize-net))

(defn robokit-train! [filename output batchsize epochs]
  (let [robokit-iterator (sequence-classification-csv-iterator filename batchsize 8 256)
        network-config (:network (load-config))]
    (time
     (-> (robokit network-config)
         (train-net! epochs robokit-iterator)
         (ai/save-model output)))
    (.reset robokit-iterator)
    (println (ai/evaluate robokit robokit-iterator))))
