# Spinnaker on Docker Compose 

This is an experimental integration of Spinnaker and Docker Compose using docker images for spinnaker published on the Docker Hub.

It requires no installation of any software aside from the toolbox to test out Spinnaker. 

These instructions have been tested on MacOS 10.11.1 and Docker Toolbox 1.9.0b

## Limitations

* The docker compose project is intended as a 'try Spinnaker now' tool. It's not intended for production use.

* Since we use an in-memory cassandra and an in-memory redis, changes to applications and pipelines will not be persisted across restarts. 

* We only support Amazon credentials set via the .aws directory or via environment variables. 

## Requirements

Install the [Docker ToolBox](https://www.docker.com/docker-toolbox)

Navigate to the config directory at the root of this project ( two directories up from the experimental folder ). Copy the `default-spinnaker-local.yml` file into a file called `spinnaker-local.yml`. Edit spinnaker-local.yml to fit your configuration.

If you're using aws, create a directory called aws under the config directory. Copy over your credentials from the .aws directory into this directory. If you don't have a .aws directory, follow the instructions here to create those credentails. 

If you're using Google Compute Platform, create a directory called gcp under the config directory. Copy your json credentails into this directory as gcp.json

# Local Installation

1. Open Kitematic and click on docker cli at the bottom
2. Export your current docker ip address as follows:
   ``` export DOCKER_IP=`docker-machine ip default` ```
3. Make sure you are under the experimental/docker-compose directory
4. Run ```docker-compose up -d``` , this will pull all the images needed to run Spinnaker from Docker Hub.
5. You should see all the containers for your microservice come up one by one in Kitematic.

## Using Spinnaker locally

Under Kitematic, click on the application 'deck' -> settings -> ports. Click on the link to see Spinnaker in action.

<img src="https://cloud.githubusercontent.com/assets/74310/11158618/4bba7122-8a0e-11e5-83b6-8ff2297562b2.png"/>

Alternatively, you can just enter ```open http://$DOCKER_IP:9000```

# Installing via Docker Compose on the Google Compute Platform

These instructions show you how to install the docker-compose setup in the cloud using docker-machine. Instructions for other providers should be similar. 

One of the trickier bits of setting up spinnaker is to get the cloud providers configured correctly. It's recommended that you test out your configuration first on your local machine and then push to the cloud once you're happy with the results. 

If you don't already have a Google Compute Platform account, you can create one [here](https://cloud.google.com/compute/). 

Create a project that will hold your Spinnaker install and keep track of the project id ( which might be different than the project name ).

## Set up Docker Machine Environment for GCP

If you haven't already, obtain and set your CCP credentials following [these instructions](https://developers.google.com/identity/protocols/application-default-credentials#howtheywork). 

Run ```docker-machine create  --driver google --google-project [your project name] --google-machine-type n1-standard-4 goocker ```

Verify that this is running correctly by running

```docker-machine ip goocker``` ( goocker is the name of my docker machine ).

You should see an IP address returned and an instance running in GCP.

## Copy configuration files to the GCP instance

The next step is to copy over the configuration files from our local machine to our instance.  We'll use the gcloud cli tool to do this. 

```gcloud compute --project "spinnakergce" copy-files ../../config/* ubuntu@goocker:~/spinnakerconfig --zone "us-central1-a"```

```gcloud compute --project "spinnakergce" copy-files compose.env ubuntu@goocker:~/spinnakerconfig --zone "us-central1-a"```

Ssh into the box:

```gcloud compute --project "spinnakergce" ssh --zone "us-central1-a" "goocker"```

And move the copied files into /root/spinnakerconfig
```sudo su
 cp -r /home/ubuntu/spinnakerconfig/ /root/spinnakerconfig/
chmod 666 /root/spinnakerconfig
chmod 444 /root/spinnakerconfig/*
```
## Configure firewall rules
This will allow the Spinnaker ports used by docker compose to become available to the outside world.

Go to your GCP admin console and click on your instance, then network name ( mine says default ). Add a firewall rule and put `0.0.0.0/0` in Source and `tcp:8080-9000` in Allowed protocols and ports. Save your firewall rules. 

## Launch Spinnaker via Docker Compose

Now that everything is set up, you should switch to using the goocker docker machine.
``` eval "$(docker-machine env goocker)" ```

Launch docker-compose using the remote configuration and the remote host ip. 
``` DOCKER_IP=`docker-machine ip goocker` docker-compose -f docker-compose.yml -f docker-compose.remote.yml up -d  ```

Once you have completed the above configuration, you should be able to deploy Spinnaker.
First, get the IP address of your remote instance via
```docker-machine ip goocker```

## Removing Docker Machine Environment

If you no longer want an instance of Spinnaker running on your GCP account, remember to disable your docker machine instance by typing:

```docker-machine rm goocker``` 

This will not remove any instances deployed by Spinnaker, only the docker compose services that were deployed.

# Working with Spinnaker and Docker Compose

## Updating Spinnaker

Call ```docker-compose pull``` to get the latest version of Spinnaker
Call ```docker-compose restart``` to restart all containers

## Stopping Spinnaker

1. Run ```docker-compose stop```
2. If you don't want to keep the containers around, use ```docker-compose rm -f```

## Helpful tips

### Adding more memory to your local machine

Spinnaker is pretty memory intensive, we suggest modifying the virtual box image used by docker machine to have more memory. You can do this by opening virtualbox and changing your base memory amount via settings -> System -> Base memory. This configuration has been tested on 8GB. 