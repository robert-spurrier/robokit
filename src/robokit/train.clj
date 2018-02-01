(ns robokit.train
  (:require [jutsu.ai.core :as ai]
            [cprop.core :refer [load-config]]
            [robokit.config :as cfg]))

(defn vel-idx
  "Get the velocity index for the corresponding drum."
  [mnote vbin]
  (-> (* mnote 12)
      (+ 8)
      (+ vbin)))

(defn update-encs
  "Flip the drum hit and velocity bits for the current event."
  [vs idx mnote vbin]
  (let [vidx (vel-idx mnote vbin)]
    (-> vs
        (assoc-in [idx mnote] 1))))

(defn encode
  "Translate input MIDI event into NN friendly format by updating zeroed out one-hot vectors vs.
   We are standardizing to a note length of 12 ticks, so we update 12 vectors per event."
  [vs e]
  (let [note (:note e)
        time (:timestamp e)
        vel (:velocity e)
        mnote (get cfg/drum-note-map note)
        vbin (Math/floorDiv vel 10)]
    (loop [x (range time (+ time 12))
           result vs]
      (if-let [idx (first x)]
        (recur (rest x) (update-encs result idx mnote vbin))
        result))))

(defn clip-tick-length
  "Fetches the max tick number for a midi clip.
   Adds 12 to ensure there is no note overflow."
  [m]
  (+ 12 (-> m :tracks first :events last :timestamp)))

(defn empty-tick-vector
  "Return a zeroed one-hot vector representing one tick of the MIDI clock.
   This 104 will need to be changed per drum mapping if we are trying to make this more flexible." 
  []
  (vec (repeat 8 0)))

(defn init-vectors
  "Given a single MIDI clip, creates zeroed out one-hot vectors for each tick in the clip."
  [m]
  (let [max-ticks (clip-tick-length m)]
    (vec (repeatedly max-ticks empty-tick-vector))))

(defn note-on-events
  "Get just the note on events from the MIDI clip.
   For our use case (drums) we are standardizing note length, so other commands can be discarded."
  [m]
  (filter
   #(= :note-on (:command %))
   (-> m :tracks first :events)))

(defn encoded-clip
  "Encodes a single MIDI drum track into one-hot vectors."
  [m]
  (reduce encode (init-vectors m) (note-on-events m)))

(defn encoded-string
  "Convert a feature feactor and a label vector into something readable by DL4j
   e.g. [[0 1 0 0 0 0 1 0] [0 1 0 0 0 0 1 0]] -> 0,1,0,0,0,0,1,0,66"
  [[fv lv]]
  (str (clojure.string/join "," fv)
       ","
       (Integer/parseInt (reduce str lv) 2)))

(defn write-training-data!
  "Given a collection of MIDI clojure maps, convert them to vectors and
   write them to files for training in DL4j"
  [f m]
  (with-open [w (clojure.java.io/writer f)]
    (doseq [line (->> m
                      (mapcat encoded-clip)
                      (partition 2 1)
                      (map encoded-string))]
      (.write w line)
      (.newLine w))))

;; For future reference:
;; 'you can also RecordReaderMultiDataSetIterator (which is a bit more powerful)
;; and wrap with MultiDataSetWrapperIterator if required'
(defn sequence-classification-csv-iterator
  "Use for Recurrent Neural Nets"
  [filename batch-size label-index num-possible-labels]
  (let [path (java.io.File. filename)
        rr (org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader. 0 ",")]
    (.initialize rr (org.datavec.api.split.FileSplit. path))
    (org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator. rr batch-size num-possible-labels label-index)))

(defn train-net! [net epochs dataset-iterator]
  (doseq [n [range 0 epochs]]
    (println (str "Epoch " n))
    (.reset dataset-iterator)
    (.fit net dataset-iterator))
  net)

(defn robokit [cfg]
  "Initialize the lstm-rnn"
  (-> cfg      
      ai/network
      ai/initialize-net))

(defn robokit-train [filename output batchsize epochs]
  (let [robokit-iterator (sequence-classification-csv-iterator filename batchsize 8 256)]
    (time
     (-> (load-config)
         :network
         (robokit)
         (train-net! epochs robokit-iterator)
         (ai/save-model output)))
    (.reset robokit-iterator)
    (println (ai/evaluate robokit robokit-iterator))))

; (robokit-train "training_data.csv" output (Integer/parseInt batch-size) (Integer/parseInt epochs))))))
