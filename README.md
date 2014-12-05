DualQRScanner
=============

An Android library for scanning documents with two QR codes. Takes a photograph of the document, and uses the two codes to orient and scale the photo to allow it to be used as a way to interact with the document without the need for more in-depth image recognition.


How do I use it?
----------------
Clone this repository and import into Android Studio. You can then make your own Dual-QR project by extending [DecoderActivity](/app/src/main/java/ac/robinson/dualqrscanner/DecoderActivity.java), or try out [TicQR](https://github.com/EnteriseToolkit/ticqr) or [PaperChains](https://github.com/EnteriseToolkit/paperchains) instead.


License
-------
Apache v2.0


Credits
-------
This library is based on [Android Quick-Response Code](https://github.com/phishman3579/android-quick-response-code), which is itself based on [ZXing](https://github.com/zxing/zxing/).
