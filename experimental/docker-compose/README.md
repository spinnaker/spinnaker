# Spinnaker on Docker Compose 

This is an experimental integration of Spinnaker and Docker Compose using docker images for Spinnaker published on the Docker Hub.

It requires no installation of any software aside from the toolbox to test out Spinnaker. 

These instructions have been tested on MacOS 10.11.1 and Docker Toolbox 1.9.0b

## Limitations

* The docker compose project is intended as a 'try Spinnaker now' tool. It's not intended for production use.

* Since we use an in-memory cassandra and an in-memory redis, changes to applications and pipelines will not be persisted across restarts. 

* We only support Amazon credentials set via the .aws directory or via environment variables. 

## Requirements

Install the [Docker ToolBox](https://www.docker.com/docker-toolbox)

Navigate to the config directory at the root of this project ( one directory up from the experimental folder ). Copy the `default-spinnaker-local.yml` file into a file called `spinnaker-local.yml`. Edit spinnaker-local.yml to fit your configuration.

If you're using aws, create a directory called aws under the config directory. Copy over your credentials from the .aws directory into this directory. If you don't have a .aws directory, follow the instructions here to create those credentials. 

If you're using Google Compute Platform, create a directory called gcp under the config directory. Copy your json credentials into this directory as gcp.json. Set your jsonPath for google in spinnaker-local.yml to be ```/root/.gcp/gcp.json```

# Local Installation

1. Open Kitematic and click on docker cli at the bottom
2. Make sure you are under the experimental/docker-compose directory
3. Run ```DOCKER_IP=`docker-machine ip default` docker-compose up -d``` , this will pull all the images needed to run Spinnaker from Docker Hub.
4. You should see all the containers for your microservice come up one by one in Kitematic.

## Using Spinnaker locally

Under Kitematic, click on the application 'deck' -> settings -> ports. Click on the link to see Spinnaker in action.

<img src="https://cloud.githubusercontent.com/assets/74310/11158618/4bba7122-8a0e-11e5-83b6-8ff2297562b2.png"/>

Alternatively, you can just enter ```DOCKER_IP=`docker-machine ip default` && open http://$DOCKER_IP:9000```

# Deploying Spinnaker on the Cloud via Docker Compose

These instructions show you how to install the docker-compose setup in the cloud using docker-machine.

One of the trickier bits of setting up spinnaker is to get the cloud providers configured correctly. It's recommended that you test out your configuration first on your local machine and then push to the cloud once you're happy with the results. 

## 1. Set up Docker Machine Environment

### Google Compute Platform ###

If you don't already have a Google Compute Platform account, you can create one [here](https://cloud.google.com/compute/). 

Create a project that will hold your Spinnaker install and keep track of the project id ( which might be different than the project name ).

If you haven't already, obtain and set your GCP credentials following [these instructions](https://developers.google.com/identity/protocols/application-default-credentials#howtheywork). 

Run ```docker-machine create  --driver google --google-project [your project id] --google-machine-type n1-standard-4 spinnakerremote ```

### Microsoft Azure

If you don't already have an Azure account, you can create one [here](https://azure.microsoft.com/en-us)

Create and add a subscription key following the instructions [here](https://docs.docker.com/machine/drivers/azure/)

Copy your subscription id

Run ```docker-machine create --driver azure --azure-subscription-id="xxxx-xxxx-xxxx-xxxx" --azure-subscription-cert="mycert.pem" --azure-location="East US"  spinnakerremote```

Wait until docker machine finished and then log into your Azure portal, select the machine that was created, click on Configure and change the machine size to be 'Standard_A6 (4 cores, 28 GB memory)'.

## 2. Copy configuration files to the remote docker machine instance

Verify that this is running correctly by running

```docker-machine ip spinnakerremote``` ( spinnakerremote is the name of my docker machine ).

You should see an IP address returned.

The next step is to copy over the configuration files from our local machine to our instance.  

```docker-machine scp -r ../../config spinnakerremote:~/spinnakerconfig```

Ssh into the box:

```docker-machine ssh spinnakerremote```

And move the copied files into /root/spinnakerconfig:

```
sudo su
cp -r spinnakerconfig/ /root/
chmod 666 /root/spinnakerconfig
chmod 444 /root/spinnakerconfig/*
```

## 3. Configure access rules

This will allow the Spinnaker ports used by docker compose to become available to your workstation.

*Note: You should be aware of the implications of opening up your virtual machines to the public internet prior to configuring firewall rules. Several more secure options (e.g. SSH tunnel, SOCKS proxy) are described [here](https://cloud.google.com/solutions/connecting-securely).*

### Google Compute Platform ###

Go to your GCP developers console and click on your instance, then network name ( it should say `default` ). Click "Add firewall rule" and fill in the following values:
* Name: `my-docker-machine`
* Source IP ranges: the ip address of your local workstation (you can find the ip address of your local workstation via `curl myip4.com`)
* Allowed protocols and ports: `tcp:8080-9000`
* Target tags: `docker-machine`

Click "Create".

### Microsoft Azure ###

Go to your Azure portal and select your remotespinnaker instance ( under Virtual Machines ).
Navigate to endpoints.
Create a new endpoint for each of following ports: 9000, 8080 and 8084
Click on Manage ACL for each of these endpoints and add a permit ACL for your local workstation  (you can find the ip address of your local workstation via `curl myip4.com`).

## 4. Launch Spinnaker via Docker Compose

Now that everything is set up, you should switch to using the spinnakerremote docker machine: ``` eval "$(docker-machine env spinnakerremote)" ```

Launch docker-compose using the remote configuration and the remote host ip: ``` DOCKER_IP=`docker-machine ip spinnakerremote` docker-compose -f docker-compose.yml -f docker-compose.remote.yml up -d  ```

Once you have completed the above configuration, you should be able to resolve the Spinnaker web application from your local workstation: ```DOCKER_IP=`docker-machine ip spinnakerremote` && open http://$DOCKER_IP:9000```

## 5. Removing Docker Machine Environment

If you no longer want an instance of Spinnaker running on your GCP account, remember to disable your docker machine instance by typing:

`docker-machine rm spinnakerremote`

This will not remove any instances deployed by Spinnaker, only the docker compose services that were deployed.

# Working with Spinnaker and Docker Compose

## Updating Spinnaker

1. Get the latest version of Spinnaker: `docker-compose pull`
2. Restart all containers: `docker-compose restart`

## Stopping Spinnaker

1. Stop all containers: `docker-compose stop`
2. If you don't want to keep the containers around, use: ```docker-compose rm -f```

## Helpful tips

### Adding more memory to your local machine

Spinnaker is pretty memory-intensive, we suggest modifying the virtual box image used by docker machine to have more memory. You can do this by opening virtualbox and changing your base memory amount via settings -> System -> Base memory. This configuration has been tested on 8GB. 
