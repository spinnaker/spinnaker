# Spinnaker on Kubernetes

This is an experimental example of how to run Spinnaker on a Kubernetes cluster using docker images
of Spinnaker components.

It requires you to have the [Google Cloud SDK](https://cloud.google.com/sdk/#Quick_Start) installed with the `kubectl` component

  ```
  $ gcloud components install kubectl
  ```

## Warning
This setup exposes the Spinnaker UI (and therefore, control over your GCE VM environments) to the world, unprotected, on a public IP address. It is not recommended to leave this exposed without other restrictions, such as authentication and/or source IP address filtering.

## Limitations

Both storage mechanisms (Cassandra and Redis) are mostly out-of-the-box, which is likely nowhere
near production grade.

## TODOs

1. Cassandra deployment options:
    1. Create own Dockerfile that executes the keyspace creation scripts (how?).
    1. Use embedded/in-memory Cassandra (like docker-compose deployment)
    1. Remove Cassandra dependency altogether
1. Add instructions for making a persistent disk (PD) for the configuration. This makes >1 node much
 easier to configure.


# Instructions

1. (Optional) Set the default zone and project. All `gcloud` commands below will require the `--zone`
parameter if you skip this step.

  ```
  $ gcloud config set compute/zone us-central1-f
  ```


## Cluster Creation

1. Create a new Kubernetes cluster running on Google Container Engine. This tutorial does a lot of
copying files to the node, so for simplicity, we will only use 1 host.

  ```
  $ gcloud container clusters create my-spinnaker-on-kubernetes --num-nodes 1 --scopes storage-rw,compute-rw --machine-type n1-standard-4
  ```

1. Get the name of the node in a variable for convenience.
  ```
  $ MY_GKE_NODE=`kubectl get nodes -o go-template='{{ (index .items 0).metadata.name }}'`
  ```


## Spinnaker Configuration

Spinnaker needs the set of [configuration files](../../config) to be available to each component. We will use the [hostPath](http://kubernetes.io/v1.1/docs/user-guide/volumes.html#hostpath) method to expose a local directory on the node to each container. We must first get these files onto the node. 

1. Create a directory on your cluster node to store the configuration.

  ```
  $ gcloud compute ssh root@$MY_GKE_NODE 'mkdir -p /root/.spinnaker'
  ```

1. Copy the contents of the `../../config` directory to the node.

  ```
  $ gcloud compute copy-files ../../config root@$MY_GKE_NODE:/root/.spinnaker
  ```

1. Edit the [spinnaker-local.yml](spinnaker-local.yml) file in this directory to include your GCP project name.

  > **Note**: This file is mostly the same as [the default](../../config/default-spinnaker-local.yml)
  spinnaker-local.yml file, but uses hostnames that will be assigned when the Kubernetes Services are created.

1. Copy the edited `spinnaker-local.yml` file to the config directory on the node.

  ```
  $ gcloud compute copy-files ./spinnaker-local.yml root@$MY_GKE_NODE:/root/.spinnaker/config
  ```

1. Create and download your JSON credentials for this project in the [Google Developers Console](https://console.developers.google.com/).

1. Create a `.gce` directory and copy this file to the config directory on the node. Name the file `gce.json`.

  ```
  $ gcloud compute ssh root@$MY_GKE_NODE 'mkdir -p /root/.spinnaker/.gce'
  $ gcloud compute copy-files /PATH/TO/MY/CREDENTIALS.json root@$MY_GKE_NODE:/root/.spinnaker/.gce/gce.json
  ```

## Deploy Spinnaker Dependencies

1. Copy the Cassandra keyspace scripts to a new directory.

  ```
  $ gcloud compute ssh root@$MY_GKE_NODE 'mkdir -p /root/cassandra'
  $ gcloud compute copy-files ../../cassandra/* root@$MY_GKE_NODE:/root/cassandra
  ```

1. Deploy the storage mechanisms to the cluster.

  ```
  $ kubectl create -f 0-dependencies.yml
  ```

1. Execute each cassandra keyspace script on the cassandra pod.

  ```
  $ CASS_NAME=`kubectl get pods -l component=cassandra -o go-template='{{ (index .items 0).metadata.name }}'`
  $ FILES=`ls -1 ../../cassandra/`
  $ for f in $FILES; do \
      kubectl exec $CASS_NAME -- cqlsh -f /root/cassandra/$f; \
    done;
  ```

1. Enable the Thrift server so that the other Java components can connect to Cassandra

  ```
  $ kubectl exec $CASS_NAME -- nodetool enablethrift
  ```

## Deploy Component `Services`

1. Deploy the `Service` representation of each Spinnaker component.

  ```
  $ kubectl create -f 1-services.yml
  ```

1. After some time, the `deck` and the `gate` services should have external IP addresses. Make note of each of these.

Gate:
  ```
  $ kubectl get svc -l component=gate -o go-template='{{ (index (index .items 0).status.loadBalancer.ingress 0).ip }}'
  ```

Deck:
  ```
  $ kubectl get svc -l component=deck -o go-template='{{ (index (index .items 0).status.loadBalancer.ingress 0).ip }}'
  ```

1. Modify [2-repControllers.yml](2-repControllers.yml) to make `gate`'s IP address the value of `deck`'s `API_HOST`
environmental variable. Look for

```
- name: API_HOST
  value: http://GATE_IP_ADDRESS_GOES_HERE:8084
```

in the configuration.

## Deploy Component `ReplicationController`s

1. Bring up the the actual containers of each component

  ```
  $ kubectl create -f 2-repControllers.yml
  ```

1. Monitor that each pod as it comes online

  ```
  $ kubectl get pods
  NAME                READY     STATUS    RESTARTS   AGE
cassandra-szyz9     1/1       Running   0          2d
clouddriver-8u4i1   1/1       Running   0          27s
deck-iwkpb          1/1       Running   0          26s
echo-n7umh          1/1       Running   0          27s
front50-i4dhl       1/1       Running   0          26s
gate-dxsti          1/1       Running   0          27s
orca-7roar          1/1       Running   0          27s
redis-qqbkq         1/1       Running   0          2d
rosco-hi2lk         1/1       Running   0          27s
rush-oy0gx          1/1       Running   0          27s
```

1. `deck` is one of the slower components to start up. Ensure you see `webpack: bundle is now VALID` at the end of the output log. Check the output with:

  ```
  $ DECK=`kubectl get pods -l component=deck -o go-template='{{ (index .items 0).metadata.name }}'` && kubectl logs -f $DECK
  ```

1. Access the Spinnaker UI with the Deck external load balancer IP address you acquired earlier: `http://1.2.3.4:9000`

  > **WARNING**: This IP address is _public_ and can now be accessed by _anyone_. Don't forget to tear it down after you're done experimenting!

1. You now have a successful Spinnaker deployment running Kubernetes! Congratulations!

1. Tear down your service with the following

  ```
  $ kubectl delete -f 2-repControllers.yml
  ```
