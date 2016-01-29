# Spinnaker on Docker Compose

This is an experimental integration of Spinnaker and Docker Compose using docker images for Spinnaker published on the Docker Hub. It requires no installation of any software aside from the Docker Toolbox to test out Spinnaker.

*Note: These instructions have been tested on Mac OS 10.11.1 and Docker Toolbox 1.9.0b, but might require slight tweaking if you are using a different platform.*

## Limitations

* The docker compose project is intended as a 'try Spinnaker now' tool. It's not intended for production use.
* Since we use an in-memory Cassandra and an in-memory Redis, changes to applications and pipelines will not be persisted across restarts.
* We only support Amazon credentials set via the .aws directory or via environment variables.

## Requirements

* Install the [Docker Toolbox](https://www.docker.com/docker-toolbox).
* Navigate to the config directory at the root of this project (one directory up from the experimental folder). Copy the `default-spinnaker-local.yml` file into a file called `spinnaker-local.yml`. Edit spinnaker-local.yml to fit your configuration.
* If you're using AWS, create a directory called aws under the config directory. Copy over your credentials from the .aws directory into this directory. If you don't have a .aws directory, follow the instructions [here](http://spinnaker.io/documentation/getting_started.html#step-1-set-up-your-target-deployment-environment) to create those credentials.
* If you're using Google Compute Platform, create a directory called gcp under the config directory. Copy your json credentials into this directory as gcp.json. Set your jsonPath for google in spinnaker-local.yml to be `/root/.gcp/gcp.json`.
* If you're using docker-machine locally, you will still need to move your config directory to the docker-machine host as stated in the [cloud guide](https://github.com/spinnaker/spinnaker/tree/master/experimental/docker-compose#2-copy-configuration-files-to-the-remote-docker-machine-instance).  Otherwise, you will not see your accounts when using Spinnaker.

# Local Installation

1. Open Kitematic and click on **'DOCKER CLI'** at the bottom.
2. Make sure you are under the experimental/docker-compose directory.
3. Run ```DOCKER_IP=`docker-machine ip default` docker-compose up -d```, this will pull all the images needed to run Spinnaker from Docker Hub.
4. You should see all the containers for your microservice come up, one by one in Kitematic.

## Using Spinnaker locally

Under Kitematic, click on **'deck' -> settings -> ports**. Click on the link to see Spinnaker in action.

<img src="https://cloud.githubusercontent.com/assets/74310/11158618/4bba7122-8a0e-11e5-83b6-8ff2297562b2.png"/>

Alternatively, you can just enter

```
open http://`docker-machine ip default`:9000
```

## Using locally built images

Sometimes it is useful to use the docker compose configuration to test changes that span multiple services locally. We provide a development configuration that will build a service from source instead of downloading an image from the remote repository. The setup is similar to the local configuration outlined at the root of this project.

* Open Kitematic and click on **'DOCKER CLI'** at the bottom.
* Create a directory called build as a sibling to the spinnaker directory.
* Run `../spinnaker/dev/refresh_source.sh --pull_origin --use_ssh --github_user default` to download Spinnaker source files.
* Navigate back to the `docker-compose` directory.

*Build a single service*

```
docker-compose -f docker-compose.dev.yml build [service name]
```

*Build all services*

```
docker-compose -f docker-compose.dev.yml build
```

*Start a service*

```
DOCKER_IP=`docker-machine ip default` docker-compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.dev.yml up [service name]
```

*Start all services*

```
DOCKER_IP=`docker-machine ip default` docker-compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.dev.yml up -d
```

*Note: It might be useful to create an alias for your docker dev environment in your shell, see the tips section at the end of this document.*

### Preloading library dependencies for development

If building from source, the gradle setup will download a wrapper and individual libraries every time you issue a build. You can avoid this by pre-caching your build directory.

1. Navigate to the build folder and select a service, for example, igor will be at build/igor.
2. Run `GRADLE_USER_HOME=cache ./gradlew compileGroovy clean -x test --refresh-dependencies`. This will download the gradle wrapper and all the dependencies for the project into the cache directory.

Now, when you build a service, it will just build the source code and not download the libraries. This will be much faster.

# Deploying Spinnaker on the Cloud via Docker Compose

These instructions show you how to install the docker-compose setup in the cloud using docker-machine. One of the trickier bits of setting up spinnaker is to get the cloud providers configured correctly. It's recommended that you test out your configuration first on your local machine and then push to the cloud once you're happy with the results.

*Note: I will use spinnakerremote as the name of my docker machine.*

## 1. Set up Docker Machine Environment

### Amazon Web Services

1. If you don't already have an AWS account, you can create one [here](https://aws.amazon.com/).
2. Create the necessary access and secret keys to provision an instance.
3. Create a VPC with an attached internet gateway following the instructions [here](http://docs.aws.amazon.com/AmazonVPC/latest/UserGuide/VPC_Internet_Gateway.html).

Run

```
docker-machine create --driver amazonec2
--amazonec2-access-key [access key] \
--amazonec2-secret-key [secret key] \
--amazonec2-vpc-id [vpc id] \
--amazonec2-instance-type m4.xlarge
spinnakerremote
```

### Google Compute Platform

1. If you don't already have a Google Compute Platform account, you can create one [here](https://cloud.google.com/compute/).
2. Create a project that will hold your Spinnaker install and keep track of the project id (which might be different than the project name).
3. If you haven't already, obtain and set your GCP credentials following [these instructions](https://developers.google.com/identity/protocols/application-default-credentials#howtheywork).

Run

```
docker-machine create --driver google \
--google-project [your project id] \
--google-machine-type n1-standard-4 \
spinnakerremote
```

### Microsoft Azure

1. If you don't already have an Azure account, you can create one [here](https://azure.microsoft.com/en-us).
2. Create and add a subscription key following the instructions [here](https://docs.docker.com/machine/drivers/azure/).
3. Copy your subscription id.

Run

```
docker-machine create --driver azure \
--azure-subscription-id="xxxx-xxxx-xxxx-xxxx" \
--azure-subscription-cert="mycert.pem" \
--azure-location="East US" \
spinnakerremote
```

Wait until docker machine finished and then log into your Azure portal

* Select the machine that was created.
* Click on Configure and change the machine size to be **'Standard_A6 (4 cores, 28 GB memory)'**.

## 2. Copy configuration files to the remote docker machine instance

Verify that this is running correctly by running

```
docker-machine ip spinnakerremote
```

You should see an IP address returned.

The next step is to copy over the configuration files from our local machine to our instance.

```
docker-machine scp -r ../../config spinnakerremote:~/spinnakerconfig
```

Ssh into the box

```
docker-machine ssh spinnakerremote
```

And move the copied files into `/root/spinnakerconfig`

```
sudo su
cp -r spinnakerconfig/ /root/
chmod 666 /root/spinnakerconfig
chmod 444 /root/spinnakerconfig/*
```

## 3. Configure access rules

This will allow the Spinnaker ports used by docker compose to become available to your workstation.

*Note: You should be aware of the implications of opening up your virtual machines to the public internet prior to configuring firewall rules. Several more secure options (e.g. SSH tunnel, SOCKS proxy) are described [here](https://cloud.google.com/solutions/connecting-securely).*

### Amazon Web Services

Go to Security Groups in the EC2 dashboard and select **'docker-machine' -> Inbound -> Edit -> Add Rule** and create the following rule

* Type: `Custom TCP Rule`
* Port Range: `8080-9000`
* Source: `My IP`

### Google Compute Platform

Go to your GCP developers console and click on your instance, then network name (it should say `default`). Click "Add firewall rule" and fill in the following values

* Name: `my-docker-machine`
* Source IP ranges: the ip address of your local workstation (you can find the ip address of your local workstation via `curl myip4.com`)
* Allowed protocols and ports: `tcp:8080-9000`
* Target tags: `docker-machine`

Click "Create".

### Microsoft Azure

Go to your Azure portal and select your spinnakerremote instance (under Virtual Machines).

* Navigate to endpoints.
* Create a new endpoint for each of following ports: 9000, 8080 and 8084.
* Click on Manage ACL for each of these endpoints and add a permit ACL for your local workstation (you can find the ip address of your local workstation via `curl myip4.com`).

## 4. Launch Spinnaker via Docker Compose

Now that everything is set up, you should switch to using the spinnakerremote docker machine

```
eval "$(docker-machine env spinnakerremote)"
```

Launch docker-compose using the remote configuration and the remote host ip

```
DOCKER_IP=`docker-machine ip spinnakerremote` docker-compose -f docker-compose.yml -f docker-compose.remote.yml up -d
```

Once you have completed the above configuration, you should be able to resolve the Spinnaker web application from your local workstation

```
DOCKER_IP=`docker-machine ip spinnakerremote` && open http://$DOCKER_IP:9000
```

## 5. Removing Docker Machine Environment

If you no longer want an instance of Spinnaker running on your GCP account, remember to disable your docker machine instance by typing

```
docker-machine rm spinnakerremote
```

This will not remove any instances deployed by Spinnaker, only the docker compose services that were deployed.

# Working with Spinnaker and Docker Compose

## Updating Spinnaker

1. Get the latest version of Spinnaker: `docker-compose pull`
2. Restart all containers: `docker-compose restart`

## Stopping Spinnaker

1. Stop all containers: `docker-compose stop`
2. If you don't want to keep the containers around, use: `docker-compose rm -f`

## Helpful tips

### Adding more memory to your local machine

Spinnaker is pretty memory intensive, we suggest modifying the virtual box image used by docker machine to have more memory. You can do this by opening virtualbox and changing your base memory amount via **Settings -> System -> Base Memory**. This configuration has been tested on 8GB.

### Add shortcuts to your shell

In my profile, I have added the following aliases

```
alias spin='DOCKER_IP=`docker-machine ip default` docker-compose'

alias spindev='DOCKER_IP=`docker-machine ip default` docker-compose -f docker-compose.yml -f docker-compose.override.yml -f docker-compose.dev.yml'

alias spinremote='DOCKER_IP=`docker-machine ip spinnakerremote` docker-compose -f docker-compose.yml -f docker-compose.remote.yml'

alias spincache='GRADLE_USER_HOME=cache ./gradlew compileGroovy clean -x test --refresh-dependencies'
```

This way, I can simply run `spindev build igor` to build my local igor file from source. Also `spin up -d` will launch my docker-compose configuration.
