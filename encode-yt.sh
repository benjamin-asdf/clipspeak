
#!/bin/sh

ffmpeg -i "$1" -c:v libx264 -preset slow -crf 18 -profile:v high -level 4.2 -pix_fmt yuv420p -c:a aac -b:a 192k "$2"
