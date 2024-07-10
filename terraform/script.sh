#!/bin/bash
set -e  # Exit immediately if a command exits with a non-zero status

# Log output to a file
exec > /var/log/startup-script.log 2>&1

echo "Starting startup script"

export DEBIAN_FRONTEND=noninteractive
sudo apt update
sudo apt upgrade -y
sudo apt install -y -q apt-transport-https ca-certificates curl software-properties-common
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg | apt-key add -
sudo add-apt-repository --yes "deb [arch=amd64] https://download.docker.com/linux/ubuntu focal stable"
sudo apt update
sudo apt install -y -q docker-ce

sudo apt install -y git
sudo git clone https://github.com/Siar-Akbayin/fog-computing-assignment.git /home/ubuntu/fog-test

cd /home/ubuntu/fog-test
sudo docker build -t fog-computing-server .


# Run the Docker container with the volume
sudo docker run -d -p 8089:8089 --name fog-server -e EDGE_DEVICE_URL=LOCAL_TUNNEL_URL/response fog-computing-server

echo "Startup script completed"
sudo touch ../script-done.txt
