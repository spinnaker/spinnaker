# Spinnaker on Kubernetes

> *NOTE:* This is not intended for production use, as both Redis and Cassandra
> are backed by memory currently, and are so far only stable in single node
> configurations. Both of these issues should be fixed soon, however.

This guide will walk you through deploying Spinnaker to a running Kubernetes 
cluster. The steps below assume that you will be using that Spinnaker installation 
to manage and deploy other applications to that same Kubernetes cluster. 
If you want to run Spinnaker on Kubernetes but deploy to another platform, 
read [how to configure](http://www.spinnaker.io/docs/target-deployment-configuration) 
it first.

## If You're Feeling Paranoid...

This setup relies on a few Docker images that you can build yourself if you
don't trust the ones I provide. 

1. `gcr.io/google-samples/cassandra:v8` can be rebuilt from
   [here](https://github.com/kubernetes/kubernetes/tree/master/examples/cassandra/image).

2. `gcr.io/kubernetes-spinnaker/cassandra-keys:v2` can be rebuilt from
   `./images/cassandra`.

3. `gcr.io/kubernetes-spinnaker/redis-cluster:v2` can be rebuilt from
   `./images/redis`.

4. `quay.io/spinnaker/PROJECT_NAME:latest` can be rebuilt at head of
   https://github.com/spinnaker/PROJECT_NAME

## Prerequisites

Make sure you have a running Kubernetes cluster, which is explained in more
detail [here](http://www.spinnaker.io/v1.0/docs/target-deployment-setup#section-kubernetes-cluster-setup).
The key takeaway is having a kubeconfig file sitting in `~/.kube/config` that
can authenticate with the cluster you want to deploy Spinnaker to. 
Once that is all squared away, make sure that running `$ kubectl config
current-context` refers to the cluster you want to have Spinnaker running in.

Next, in the editor of your choice, open up `./config/clouddriver.yml`, and
examine the `dockerRegistry` subsection. You'll find each Spinnaker image
listed here, which will act as your available list of deployable images. Feel
free to make any changes to this section, but if you want to deploy these
listed images, you'll need to first make sure you have a [quay.io](https://quay.io)
account, and that its authentication details are filled in the 
respective `username`, `password`, and `email` fields. If you want to use an
entirely different provider or set of images, update the `address` and
`repositories` fields accordingly.

If you feel like changing the value of `kubernetes.accounts[0].name`, make sure it's reflected in
`./config/settings.js` under `providers.kubernetes.defaults.account` (this way
your account name is always prepopulated).

## Initial Startup

```
$ bash scripts/startup-all.sh  # this takes a little while...
$ bash scripts/connect.sh deck 9000 # leave this running, open a new terminal, and run
$ bash scripts/connect.sh gate 8084 # leave this running too...
``` 

Note, deck and gate may not be up immediately, wait until 

```
$ kubectl get pods --namespace=spinnaker
```

shows that each container is ready before opening the connections above.

Now point your browser at [localhost:9000](http://localhost:9000), and you're all set!

## What Just Happened?

The scripts created a namespace `spinnaker`, and deployed a `data` application
containing Redis and Cassandra, and a `spkr` application, containing all of the
Spinnaker components. All the yaml files in `./config/` were placed into a
secret called `spinnaker-config`, and your kubeconfig was placed into a secret
called `kube-config`. These were mounted at `/opt/spinnaker/config/` and
`/root/.kube` respectively in each application container.

## I Want to Update My Config...

Any time you want changes that you made to the config files to show up in
Spinnaker, you need to run

```
$ bash scripts/update-config.sh # this recreates the secrets described above
$ bash scripts/update-component.sh <component name> # for each component whose config you touched
```

The only files you should change are the `-local.yml` files, the rest are
pulled in by the `update-config.sh` script, and will overwrite any local
changes you have.

## Cleanup

If you want to delete everything, run

```
$ bash scripts/cleanup-all.sh # This deletes everything in the spinnaker namespace
```

If you just want to delete the Spinnaker components, but leave the persistence
mechanisms (Redis & Cassandra), run

```
$ bash scripts/cleanup-spinnaker.sh # This deletes everything with application name spkr
```
