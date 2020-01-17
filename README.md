# Seismo
Smartphone Pulse Transit Time Sensing

Seismo is a smartphone application that uses seismocardiography (SCG) and photoplethysmography (PPG) to
compute pulse transit time. See [our paper](https://ubicomplab.cs.washington.edu/publications/seismo/) for more details.


## How it works

Pulse transit time (PTT) is inversely correlated with blood pressure, making it
a promising technique for non-invasive, continuous estimation of blood pressure.
Seismo measures PTT using a smartphone's built-in sensors. The accelerometer is
used to detect the physical vibration of the heart when the phone is placed on
the user's chest; from this signal, called seismocardiography (SCG), the
time of the aortic valve opening can be detected. Simultaneously, the camera and
flash are used to illuminate the capillaries in the fingertip, enabling
detection of the pulse's arrival at the finger. The difference in timing between
the pulse origin at the heart and its arrival at the fingertip is the PTT. After
an individualized calibration, the PTT reading can be converted to an
approximate blood pressure reading.
