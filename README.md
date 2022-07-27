This project is a demo for multiple features.

Library Used: 
FFMPEG mobile kit: https://github.com/tanersener/ffmpeg-kit
Android CameraX

# Creating local client server communication
-> Click "start server" button to start a local server
-> We can send message to server and it revert the same message to client

# Stream local video over UDP using FFMPEG
-> pick video file using "Pick file and stream" button
-> it will stream the file over UDP on localhost
-> check the android studio logcat for checking the FFMPEG logs
-> Stop the currently streaming local video over UDP using "Stop FFMPEG" button

# Stream Android camera over UDP using FFMPEG
-> Stream Android Camera using "Stream Android Camera" button
-> FFMPEG stream Android Camera over UDP
-> Check the output on VLC player by entering stream url "udp://@localhost:8089"