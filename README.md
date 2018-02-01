# Robokit

[![robokit](https://i.imgur.com/fVCKnIH.jpg)](https://i.imgur.com/fVCKnIH.jpg)

Robokit is a neural network powered AI that generates MIDI drum loops (in a hip-hop style). Written in Clojure.

  - Click a button
  - Get a MIDI file
  - Magic

Given the simple network configuration, simple data model, limited training data; it performs surprisingly well! With a little touching up these loops can serve as a creative launchpad for your percussive ideas.

### Tech

Robokit is a simple LSTM neural network with a single hidden layer. A Clojure wrapper for Deeplearning4j was used for building and training the network. Read more here: [jutsu.ai] 
The trained, serialized model is included in the `resources/model` directory.

### Training Data

The LSTM network was trained on 65 unquantized hip-hop MIDI drum loops. They are included for reference in the `resources/midi` folder. Functions related to converting the training data and training the network live in the `src/robokit/midi.clj` and `src/robokit/train.clj` namespaces.

### Usage

 A simple GUI interface is provided that allows the user to generate random hip-hop MIDI drum loops by sampling from the network. 

### Installation

Robokit requires a recent Java Runtime Environment (JRE) install on your machine. After this prerequisite is met, clone or download this repository and run the `robokit_executable` file located in the root directory.

### Todos
 - Expand data model to include velocity information
 - Expand training dataset
 - Experiment with a more complicated network structure on an AWS P2 instance


[//]: # (These are reference links used in the body of this note and get stripped out when the markdown processor does its job. There is no need to format nicely because it shouldn't be seen. Thanks SO - http://stackoverflow.com/questions/4823468/store-comments-in-markdown-syntax)


   [jutsu.ai]: <https://github.com/hswick/jutsu.ai>