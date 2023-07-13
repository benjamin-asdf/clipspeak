import socket
import json

# Create a socket connection to the mpv IPC server
sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
sock.connect("/tmp/mpvsocket")

# Prepare the command to get playback position (timestamp)
msg = {'command': ['get_property', 'playback-time']}
sock.send(json.dumps(msg).encode('utf-8'))
sock.send(b'\n')

# Receive the response
data = sock.recv(4096)
response = json.loads(data.decode('utf-8'))

# Extract the timestamp
timestamp = response.get('data')

print(timestamp)
