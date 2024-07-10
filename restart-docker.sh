sudo docker stop fog-server
sudo docker commit fog-server fog-computing-server-saved
sudo docker rm fog-server
sudo docker run -d -p 8089:8089 --name fog-server -e EDGE_DEVICE_URL=https://cold-crabs-drop.loca.lt/response fog-computing-server-saved
