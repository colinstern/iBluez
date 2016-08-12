This android app implements bluetooth file transfer for the iShadow wearable gaze tracker.

#Setup

Start the btserver script on iShadow and enter the bluetooth address of the android you wish to connect to into the bdaddress variable. Enter the data you wish to send - or a link to it - in the argument of the method SendAsPackets(char *message). *Keep in mind that the data must be a character array.

Run the android app and tap connect. The name of our bluetooth module is "Amp'ed Up!". Tap it if it is paired or tap "Scan for devices" to discover nearby devices. The app will connect and begin receiving data and saving it to a file. The screen will display data in raw form as it comes in, how many bytes were just read, the number of the packet in the transmission sequence and whether the packet was transmitted with no error, e.g. the hashes match (the hash in the header of the packet matches the hash of the data made by the android). If there is an error in a packet, the android will request for the microcontroller to resend it. When you tap disconnect, the app will read the data back to you. This can be disabled by removing the call to printSavedData in ConnectedThread.

#Implementation notes:

Make sure the bluetooth address of the android phone is correctly entered on the microcontroller, or it will not be able to connect.

There is a one second delay on the microcontroller after it receives acknowledgement from the android that the packet was received. This can probably be reduced, but not eliminated as that would lead to bytes being dropped.

The hash is a sum of the ASCII values of each character. This is an admittedly bad hash, but can easily be fixed by changing the contents of the hash function on both the microcontroller and android.

Data is sent in packets of 256 bytes, not including the header and separator characters. As the number of the packet increases and the hash changes between packets, the total bytes per packet is usually 266 or 267 bytes.

The android uses '|' (pipe) as start and separator characters and '$' (dollar sign) as end-of-packet character. This could lead to problems if these characters are transmitted in the data. To change the separator and stop characters to something unlikely to show up in data will depend on the data being sent. Changing these characters to be a series of characters, e.g. '||' or '$$$' might work just as well.

Data is stored in a new file on the iShadow Received Data directory in the Documents folder of the android external memory every time the app is restarted. Old files may need to be cleaned out every once in a while.

If the directory is empty, the first file to be stored will be "data_0", and the next will be "data_1", and so on.

The button marked with a bluetooth symbol makes the android discoverable.

The name "Mascara" is a ploy on "iShadow" (eye shadow).

#Known Bugs

The first packet received by the app may be duplicated. This is easy to resolve in the output file by deleting the first 256 bytes.
