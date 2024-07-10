run: ```lt --port 8000```

copy printed url (e.g. https://shaky-cooks-smash.loca.lt)and add to script.sh (LOCAL_TUNNEL_URL) but keep /response

```bash

cd terraform
terraform init
terraform apply -var gcp_project_id=YOUR_GCP_PROJCT_ID -auto-approve
```
Copy outputted IP and add it to EdgeDevice.java (line 19)

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

Now the CloudComponent.java is running in the VM with Docker and we can start the EdgeDevice.java (from root)

```bash
java src/EdgeDevice.java
```

Now we can start the sensors (from root)

```bash
java src/Sensor.java
```





Useful commands:

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