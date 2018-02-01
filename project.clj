(defproject robokit "0.1.0-SNAPSHOT"
  :description "LSTM network that generates hip-hop MIDI drums"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [overtone/midi-clj "0.5.0"]
                 [hswick/jutsu.ai "0.1.1"]
                 [org.nd4j/nd4j-native-platform "0.8.0"]
                 [seesaw "1.4.5"]
                 [cprop "0.1.11"]]
  :plugins [[lein-bin "0.3.5"]]
  :main robokit.core
  :target-path "target/%s"
  :bin {:name "robokit_executable"}
  :profiles {:uberjar {:aot :all}})
