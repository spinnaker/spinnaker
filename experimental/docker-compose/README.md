# Spinnaker and Docker Compose 

This is an experimental integration of Spinnaker and Docker Compose using docker images for spinnaker published on the Docker Hub.

It requires no installation of any software aside from the toolbox to test out Spinnaker. 

These instructions have been tested on MacOS 10.11.1 and Docker Toolbox 1.9.0b

## Limitations

* The docker compose project is intended as a 'try Spinnaker now' tool. It's not intended for production use.

* Since we use an in-memory cassandra and an in-memory redis, changes to applications and pipelines will not be persisted across restarts. 

* We only support Amazon credentials set via the .aws directory or via environment variables. 

## Requirements

Make sure you're happy with the configuration of your spinnaker-local.yml file at ../../config. The instructions to change this are in the file default-spinnaker-local.yml

Make sure you have followed the setup needed at the Cloud level from the directions at the root of this project's README file. 

Install the [Docker ToolBox](https://www.docker.com/docker-toolbox)

## Starting Spinnaker

1. Open Kitematic and click on docker cli at the bottom
2. Make sure you are under the experimental/docker-compose directory
3. Run ```DOCKER_IP=`docker-machine ip default` docker-compose up -d``` , this will pull all the images needed to run Spinnaker from Docker Hub.
4. You should see all the containers for your microservice come up one by one in Kitematic.

## Using Spinnaker

Under Kitematic, click on the application 'deck' -> settings -> ports. Click on the link to see Spinnaker in action.

<img src="https://cloud.githubusercontent.com/assets/74310/11158618/4bba7122-8a0e-11e5-83b6-8ff2297562b2.png"/>

Alternatively, you can just enter ```open http://$DOCKER_IP:9000```

## Updating Spinnaker

Call ```docker-compose pull``` to get the latest version of Spinnaker
Call ```docker-compose restart``` to restart all containers

## Stopping Spinnaker

1. Run ```docker-compose stop```
2. If you don't want to keep the containers around, use ```docker-compose rm -f```

## Helpful tips

### Adding more memory

Spinnaker is pretty memory intensive, we suggest modifying the virtual box image used by docker machine to have more memory. You can do this by opening virtualbox and changing your base memory amount via settings -> System -> Base memory. This configuration has been tested on 8GB. 

### GCE Setup

If you're configuring the jsonpath for clouddriver for a gce installation, keep in mind that this directory will not be visible to the virtual machine used by Kitematic. To make the json file available, add a volume path under the clouddriver service. For example: 
```volumes: 
   - "../../config:/root/.spinnaker"
   - "~/.aws:/root/.aws"
   - "~/gce/json/:/root/gce"
```
In the example, we're mapping a new directory in your user.home/gce/json to point to /root/gce for clouddriver. In your spinnaker-local.yml, point your jsonPath to /root/gce/<name of your archive>
