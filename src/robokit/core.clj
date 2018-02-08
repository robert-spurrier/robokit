(ns robokit.core
  (:require
   [robokit.midi :as midi]
   [seesaw.mouse :as sm]
   [seesaw.core :as sc]
   [jutsu.ai.core :as ai]
   [cprop.core :refer [load-config]])
  (:import [org.nd4j.linalg.factory
            Nd4j])            
  (:gen-class))

(defn tick->event
  "Convert a single drum hit at the current timestep/tick into the appropriate
   note event and add it to the output sequence."
  [note-events timestamp drum-note previous-tick-value current-tick-value]
  (let [event-fn (fn [c]
                   (conj note-events
                         {:command c
                          :note drum-note
                          :timestamp timestamp
                          :velocity 127}))]
    (cond
      (and (= 0 previous-tick-value)
           (= 1 current-tick-value)) (event-fn :note-on)
      (and (= 1 previous-tick-value)
           (= 0 current-tick-value)) (event-fn :note-off)
      :else note-events)))

(defn decode
  "Convert the sequence of values for a given drum into a set of note events for
   that drum."
  [midi-mappings-output drum-vectors note-events current-drum-index]
  (loop [timestamp 0
         previous-tick-value 0
         ticks drum-vectors
         result note-events]
    (if-let [tick (first ticks)]
      (let [current-tick-value (get tick current-drum-index)
            drum-note (get midi-mappings-output current-drum-index)
            updated-note-events (tick->event result timestamp drum-note previous-tick-value current-tick-value)]
        (recur (inc timestamp)
               current-tick-value
               (rest ticks)
               updated-note-events))             
      result)))

(defn decoded-sequence
  "Converts a seqeuence of drum vectors into a structure that is easier to use
   with the Java MIDI API."
  [midi-mappings-output drum-vectors]
  (reduce (partial decode midi-mappings-output drum-vectors)
          []
          (range 0 8)))

(defn category->vector
  "Convert a drum category from the range of 0 to 256 into a vector of 8
   binary digits representing the drum kit state at that timestep."
  [c]
  (->> (Integer/toString c 2)
       (Integer/parseInt)
       (format "%08d")
       (mapv (comp read-string str))))

(defn vectors->indarray
  "Converts drum hit vectors into INDArray primers for sequence generation."
  [drum-vectors]
  (Nd4j/create (into-array (map float-array drum-vectors))))

(defn output-distribution [model input-vectors]
  "The categorical probabilities spit out by the softmax layer.
   These are used to determine what the model thinks the next step in a sequence
   will be."
  (let [input (vectors->indarray input-vectors)
        output (.rnnTimeStep model input)]
    (vec (.getRow output 0))))

(defn sample-distribution
  "Pick the next drum step by sampling from the softmax layer output probabilities:
   1. Locate the CDF value that is less than or equal to a randomly chosen threshold.
   2. Return the category (index) corresponding to that CDF value."
  [distribution rng]
  (let [threshold (.nextDouble rng)
        length (count distribution)]
    (loop [idx 0
           sum 0]
      (if (<= threshold sum)
        (dec idx)
        (recur (inc idx) (+ sum (get distribution idx)))))))

(defn generate-sequence
  "Generate a vector sequence of length n from the given model and primer sequence."
  [model rng primer len]
  (do
    (.rnnClearPreviousState model)
    (let [primer-output-dist (output-distribution model primer)
          primer-next-index (sample-distribution primer-output-dist rng)
          primer-next-vector (category->vector primer-next-index)]
      (loop [timestep 0
             input (subvec (conj primer primer-next-vector) 1)
             output []]
        (if (>= timestep len)
          output
          (let [next-output-dist (output-distribution model input)
                next-drum-index (sample-distribution next-output-dist rng)
                next-drum-vector (category->vector next-drum-index)]
            (recur (inc timestep)
                   (subvec (conj input next-drum-vector) 1)
                   (conj output next-drum-vector))))))))

(defn random-drum-sequence-fn
  "Returns a function that generates a random drum sequence and saves to a file."
  [midi-mappings-output model]
  (fn [e]
    (let [rng (java.util.Random.)
          primer-index (.nextInt rng 256)
          primer [(category->vector primer-index)]]
      (-> (generate-sequence model rng primer 1536)
          ((partial decoded-sequence midi-mappings-output))
          (midi/write-midi-sequence! (str "robokit_midi"
                                          "_primer_" primer-index
                                          ".mid"))))))

(defn -main
  "Load the model and present a simple push-button GUI."
  [& args]
  (let [config (load-config)
        model-config (config :model)
        midi-mappings-output (get-in config [:midi-mappings :output])
        model (->> model-config
                   (.getResourceAsStream (.getContextClassLoader (Thread/currentThread)))
                   (ai/load-model))
        gui-frame (sc/frame :title "Robokit")
        gui-button (sc/button :text "Generate!")]
    (sc/listen gui-button :action (random-drum-sequence-fn
                                   midi-mappings-output
                                   model))
    (sc/config! gui-frame :content gui-button)
    (sc/config! gui-frame :size [300 :by 100])
    (sc/show! gui-frame)))
