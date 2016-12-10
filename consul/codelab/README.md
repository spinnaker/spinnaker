# GCE Consul Codelab

The goal of this codelab is to teach you how Consul works alongside Spinnaker
with respect to service discovery. At a high level, Consul allows instances to
find healthy instances in a particular "service". Spinnaker reports which
instances are registered with and serving traffic in Consul, as well as allow
the user to toggle which sets of instances are discoverable using Consul.

> This codelab assumes no prior knowledge of Consul, but assumes you have used
> Spinnaker before. It's possible to follow along if this is your first time
> using both tools, but not recommended.

## Spinnaker Setup

The first thing we need is an instance of Spinnaker running in the same network
as the instances it will deploy.

```bash

SPINNAKER_VM_NAME=consul-spinnaker-codelab

# create a VM running spinnaker - this will take a few minutes
gcloud compute instances create $SPINNAKER_VM_NAME \
    --image spinnaker-latest-stable-licensed \
    --image-project click-to-deploy-images \
    --scopes compute-rw,storage-rw \
    --machine-type n1-highmem-4 \
    --metadata startup-script=/opt/spinnaker/install/first_google_boot.sh,consul_enabled=true

# connect to our instance of Spinnaker - it will take a few minutes
# before Spinnaker is reachable at localhost:9000
gcloud compute ssh $SPINNAKER_VM_NAME \
	--ssh-flag "-L 8084:localhost:8084" \
	--ssh-flag "-L 9000:localhost:9000"
```

## Consul Setup

The high level Consul architecture has two components:

1. The group of instances running as the __server__ nodes, acting as the
   leaders in your Consul cluster.

2. Every instance you intend to be discoverable, running as __client__
   nodes.

### Consul Server Setup

We will first setup the __server__ nodes by deploying 3 nodes and manually
having them contact one another to start the cluster. This is a one-time setup
cost we need to incur to get Consul running in your network.

```bash
# create an instance template describing the consul server instance
#
# we will be using an image we've created with consul installed, and configured
# to start as a server node.
gcloud compute instance-templates create consul-server \
    --machine-type n1-standard-1 \
    --image-project marketplace-spinnaker-release \
    --image consul-server

gcloud compute instance-groups managed create consul-server \
    --template consul-server \
    --size 3
```

Once those commands have completed, you'll want to keep track of the names of
instances in the new instance group like so:

```bash
gcloud compute instance-groups list-instances consul-server
```

This command will return a list of instances (but with different `-XXXX`
extensions)

```
NAME                STATUS
consul-server-1111  RUNNING
consul-server-2222  RUNNING
consul-server-3333  RUNNING
```

In order to complete the setup, we will ssh into the first VM, and connect to
the second two (your instance names will vary):

```
gcloud compute ssh consul-server-1111

# ssh'd into the first VM
consul join consul-server-2222
consul join consul-server-3333
```

The joins are symmetric, so you are all set!

### Consul Client Setup

We will create a base-image with Consul installed and able to autojoin our
cluster that will serve as the base image for all other instances in our
application. This is again a one-time setup cost to get Consul working.

Since we want our __client__ to autojoin the cluster on startup, we need to
provide each instance with configuration indicating where to find the
__server__ nodes (or any other __client__ nodes for that matter, but we
generally treat those as fungible). So now we need create a `join.json` file
with the following contents, replacing your __server__ node names where
necessary:

```json
{
    "start_join": [
        "consul-server-1111",
        "consul-server-2222",
        "consul-server-3333"
    ]
}
```

Let's assume that we are creating a base-image for a team that's running a
service on port 80, and we want this image to register itself with a service
named `myapp` on startup. To do so, we also need to create this `myapp.json`
file:

```json
{
    "service": {
        "name": "myapp",
        "port": 80,
        "checks": [
            {
                "id": "myapp-connect",
                "tcp": "localhost:80",
                "interval": "30s",
                "timeout": "1s"
            }
        ]
    }
}
```

Now to create the image...

```bash
# create a client node we will use to capture an image, with a bootdisk
# that won't be auto-deleted
gcloud compute instances create consul-client-myapp \
    --image-project marketplace-spinnaker-release \
    --image consul-client \
    --no-boot-disk-auto-delete

# This copies the join & myapp files onto our new instnace
gcloud compute copy-files join.json consul-client-myapp:~/
gcloud compute copy-files myapp.json consul-client-myapp:~/

gcloud compute ssh consul-client-myapp

# ssh'd into the demo VM (we don't have permission to copy the file into this
# protected part of the filesystem remotely).
sudo mv join.json /etc/consul.d/client/
sudo mv myapp.json /etc/consul.d/client/
sudo chown consul:consul /etc/consul.d/client/*.json

# close the connection
exit

# delete the instance to capture the disk
echo y | gcloud compute instances delete consul-client-myapp

# capture the image
gcloud compute images create consul-client-myapp \
    --source-disk consul-client-myapp

# delete the old boot disk
echo y | gcloud compute disks delete consul-client-myapp
```

## Deploying our Application

We will use the `consul-client-myapp` image we created as the base VM image
that all our applications are installed on to guarantee that each instance
deployed by Spinnaker joins the Consul cluster. This setup ensures that no
extra work is required by any application's developer. To do this, we need to
configure Rosco, Spinnaker's bakery.

### Configuring Rosco

> On the VM running Spinnaker (that we ssh'd into in the very first step).

We need to create the following file `/opt/rosco/config/rosco-local.yml` with
the contents

```yaml
google:
  enabled: true
  gce:
    bakeryDefaults:
      zone: us-central1-f
      network: default
      useInternalIp: false
      templateFile: gce.json
      baseImages:
      - baseImage:
          id: consul
          shortDescription: v0.6.4                    # consul version
          detailedDescription: Consul Client v0.6.4
          packageType: deb
        virtualizationSettings:
          sourceImage: consul-client-myapp            # image created above
```

Make sure that file is readable by Spinnaker:

```bash
sudo chown spinnaker:spinnaker /opt/rosco/config/rosco-local.yml
```

### Creating our Deploy Pipeline

(Include images of selecting `nginx` as the repo)
