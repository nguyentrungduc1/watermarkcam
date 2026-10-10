# Skin Segmentation

`selfie_multiclass_256x256.tflite` is Google's MediaPipe SelfieMulticlass model.
Source: https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/1/selfie_multiclass_256x256.tflite
Documentation and model card: https://developers.google.com/edge/mediapipe/solutions/vision/image_segmenter

Class 3 is face skin. Use floating-point confidence, not a binary class boundary.
The bundled model runs locally on saved photos; no face image is uploaded.
