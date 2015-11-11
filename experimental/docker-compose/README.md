Spinnaker and Docker Compose 
----------------------------


This is an experimental integration of Spinnaker and Docker Compose using docker images for spinnaker published on the Docker Hub.

It requires no installation of any software aside from the toolbox to test out Spinnaker. 

These instructions have been tested on MacOS 10.11.1 and Docker Toolbox 1.9.0b

----------------------------------------

Instructions.
-------------

Prerequisites:
-------------

Make sure you're happy with the configuration of your spinnaker-local.yml file at ../../config. The instructions to change this are in the file default-spinnaker-local.yml

Also make sure you have followed the setup needed at the Cloud level from the directions at the root of this project's README file. 

1. Install the [Docker ToolBox](https://www.docker.com/docker-toolbox)

Starting Spinnaker
------------------

1. Open Kitematic and click on docker cli at the bottom
2. Export your current docker ip address as follows:
   ``` export DOCKER_IP=`docker-machine ip default` ```
3. Make sure you are under the experimental/docker-compose directory
4. Run ```docker-compose up -d``` , this will pull all the images needed to run Spinnaker from Docker Hub.
5. You should see all the containers for your microservice come up one by one in Kitematic.

Using Spinnaker.
----------------

Under Kitematic, click on the application 'deck' -> settings -> ports. Click on the link to see Spinnaker in action.

Alternatively, you can just enter ```open http://$DOCKER_IP:9000```

Updating Spinnaker
------------------

Call ```docker-compose pull``` to get the latest version of Spinnaker
Call ```docker-compose restart``` to restart all containers

Stopping Spinnaker
------------------

1. Run ```docker-compose stop```
2. If you don't want to keep the containers around, use ```docker-compose rm -f```
