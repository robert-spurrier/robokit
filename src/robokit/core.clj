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
  [cfg coll timestamp drum-index previous-tick current-tick]
  (let [event-fn (fn [c]
                   (conj coll
                         {:command c
                          :note (get cfg drum-index)
                          :timestamp timestamp
                          :velocity 127}))]
    (cond
      (and (= 0 previous-tick)
           (= 1 current-tick)) (event-fn :note-on)
      (and (= 1 previous-tick)
           (= 0 current-tick)) (event-fn :note-off)
      :else coll)))

(defn decode
  [cfg vs coll idx]
  (loop [tick-num 0
         prev 0
         ticks vs
         result coll]
    (if-let [tick (first ticks)]
      (let [tick-hit (get tick idx)
            updated-result (tick->event cfg result tick-num idx prev tick-hit)]
       (recur (inc tick-num)
              tick-hit
              (rest ticks)
              updated-result))             
      result)))

(defn decoded-sequence
  [v cfg]
  (let [events (reduce (partial decode cfg v) [] (range 0 8))]
    {:type :midi-sequence
     :division-type :PPQ
     :resolution 96
     :tracks [{:type :midi-track
               :size 112
               :events events}]}))

(defn category->vector [c]
  (->> (Integer/toString c 2)
       (Integer/parseInt)
       (format "%08d")
       (mapv (comp read-string str))))

(defn vectors->indarray
  "Converts drum hit vectors into INDArray primers for sequence generation."
  [vs]
  (Nd4j/create (into-array (map float-array vs))))

(defn activations [network input-vectors]
  (let [input (vectors->indarray input-vectors)
        activs (.rnnTimeStep network input)]
    (vec (.getRow activs 0))))

(defn sample-distribution
  [a rando]
  (let [threshold (.nextDouble rando)
        length (count a)]
    (loop [idx 0
           sum 0]
      (if (<= threshold sum)
        (dec idx)
        (recur (inc idx) (+ sum (get a idx)))))))

(defn generate-sequence [network rng primer-vectors length]
  (do
    (.rnnClearPreviousState network)
    (let [primer-activs (activations network primer-vectors)
          sampled-index (sample-distribution primer-activs rng)]
      (loop [timestep 0
             input (subvec (conj primer-vectors (category->vector sampled-index)) 1)
             output []]
        (if (>= timestep length)
          output
          (let [next-activs (activations network input)
                next-index (sample-distribution next-activs rng)
                next-vector (category->vector next-index)]
           (recur (inc timestep) (subvec (conj input next-vector) 1) (conj output next-vector))))))))

(defn -main
  "Load the model and present a simple push-button GUI."
  [& args]
  (let [c (load-config)
        m (->> (c :model)
           (.getResourceAsStream (.getContextClassLoader (Thread/currentThread)))
           (ai/load-model))                  
        f (sc/frame :title "Robokit")
        b (sc/button :text "Generate!")]
    (sc/listen b :action (fn [e]
                           (let [[x y] (sm/location)
                                 seed (+ x y)
                                 rng (java.util.Random. seed)
                                 primer-index (.nextInt rng 256)
                                 primer [(category->vector primer-index)]]
                             (-> (generate-sequence m rng primer 768)
                                 (decoded-sequence (get-in c [:midi-mappings :output]))
                                 (midi/write-midi-clj! (str "robokit_midi_seed_" seed
                                                            "_primer_" primer-index
                                                            ".mid"))))))
    (sc/config! f :content b)
    (sc/config! f :size [300 :by 100])
    (-> f sc/show!)))
