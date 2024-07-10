# Temperature Measurement Network
The Temperature Measurement Network consists of three major components: Sensors, EdgeDevice, and Cloud Component

### Sensors
- Two sensors simulate temperature measurements every 5 seconds.
- Measured temperatures are sent to the EdgeDevice.

### Edge Device
- Receives temperature data from the sensors.
- Periodically calculates the average temperature from the received data.
- Sends the average temperature to the Cloud Component.

### Cloud Component
- Receives average temperatures from the EdgeDevice.
- Processes the received data.
- Sends a warning back to the EdgeDevice if the temperature exceeds a certain threshold.

Each component implements reliable messaging by caching data if no acknowledgment is received after sending a message. 
Data transmission is periodically retried until successful, ensuring no data is lost.

## Setup Instructions
### Step 1: Run Local Tunnel
run: ```lt --port 8000```

copy printed url (e.g. https://shaky-cooks-smash.loca.lt)and add to script.sh (LOCAL_TUNNEL_URL) but keep /response

### Step 2: Initialize and Apply Terraform
```bash

cd terraform
terraform init
terraform apply -var gcp_project_id=YOUR_GCP_PROJCT_ID -auto-approve
```
Copy outputted IP and add it to EdgeDevice.java (line 19)

### Step 3: Wait for VM Creation
Wait 5-10 minutes for the VM to be created and the server to be up and running

You can check if this already happened by SSHing into the VM 

```bash
gcloud compute ssh --zone "europe-west3-a" "fog-server" --project "YOUR_GCP_PROJECT_ID"
```

and navigating to the /home/ubuntu/ directory where we cloned the repo and checking if the script-done.txt file is present
```bash
cd /home/ubuntu/
ls
```
### Step 4: Start Edge Device
Now the CloudComponent.java is running in the VM with Docker and we can start the EdgeDevice.java (from root)

```bash
java src/EdgeDevice.java
```

Now we can start the sensors (from root)

```bash
java src/Sensor.java
```


## Test the Caching Mechanisms

### Test cache of sensors
If the edge device is not reachable for the sensors, the produced data will be stored in a cache file until the edge 
device is reachable again. To test this, you can stop the edge device by pressing `Ctrl + C` in the terminal where the
edge device is running. The sensors will then produce data and store it in the cache file. When the edge device is started again, 
the data will be sent to the edge device. If the cache file contains more than 25 entries, the oldest 25 entries will be sent first,
and then the sensor will wait before sending the next batch to not overload the edge device.

### Test cache of edge device
If the cloud component is not reachable for the edge device, the data will be stored in a cache file until the cloud 
component is reachable again. To test this, you can disconnect you local machine from the internet for a certain time period. 
After reconnecting to the internet, the edge device will send the data to the cloud component. If the cache file contains more 
than 25 entries, the oldest 25 entries will be sent first, and then the edge device will wait before sending the next batch to not
overload the cloud component.

### Test cache of cloud component
The testing of the cloud component cache is more difficult, as we use localtunnel to expose the edge device to the internet. 
To test the cache of the cloud component, you can stop the edge device by pressing `Ctrl + C` in the terminal where the localtunnel
command is running. The cloud component will then not receive any data from the edge device. The data will be stored in a cache file
until the edge device is reachable again. However, if the localtunnel command is being executed again, the edge device will receive 
a new URL and the cloud component will not be able to send the data to the edge device. To make the cloud component send the data to
the new URL without using the data of the cache file, you need to SSH into the instance and run the following commands:
```bash
sudo docker stop fog-server
sudo docker commit fog-server fog-computing-server-saved
sudo docker rm fog-server
sudo docker run -d -p 8089:8089 --name fog-server -e EDGE_DEVICE_URL=ADD_NEW_URL_HERE/response fog-computing-server-saved
```
Afterwards, the cloud component will send the data to the new URL from the cache file.

## Cleanup
To delete all cloud resources, run
```bash
terraform destroy -var gcp_project_id=YOUR_GCP_PROJECT_ID -auto-approve
```

## Useful commands:

For debugging the server, SSH into the VM and run 
```bash
sudo docker ps
```
Copy the container ID and run
```bash
sudo docker exec -it CONTAINER_ID bash -c 'cat /var/log/cloud_component.log'
```
You can see the cache file content the same way
```bash
sudo docker exec -it f2ca7dcbe449 bash -c 'cat /usr/src/myapp/warning_cache.txt'
```
