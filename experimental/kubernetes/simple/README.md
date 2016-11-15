# Spinnaker on Kubernetes

This guide will walk you through deploying Spinnaker to a running Kubernetes
cluster. The steps below assume that you will be using that Spinnaker installation
to manage and deploy other applications to that same Kubernetes cluster.
If you want to run Spinnaker on Kubernetes but deploy to another platform,
read [how to configure](http://www.spinnaker.io/docs/target-deployment-configuration)
it first.

## If You're Feeling Paranoid...

This setup relies on a few Docker images that you can build yourself if you
don't trust the ones I provide.

1. `gcr.io/kubernetes-spinnaker/redis-cluster:v2` can be rebuilt from
   `./images/redis`.

2. `quay.io/spinnaker/SERVICE:master` can be rebuilt at head of
   https://github.com/spinnaker/SERVICE

## Configuring Kubernetes

> __Note__ Only make changes to the files suffixed with `-local.yml` in `./config/`,
> every other file is liable to be deleted/overwritten.

Make sure you have a running Kubernetes cluster, which is explained in more
detail [here](http://www.spinnaker.io/v1.0/docs/target-deployment-setup#section-kubernetes-cluster-setup).
The key takeaway is having a kubeconfig file sitting in `~/.kube/config` that
can authenticate with the cluster you want to deploy Spinnaker to.
Once that is all squared away, make sure that running `$ kubectl config
current-context` refers to the cluster you want to have Spinnaker running in.
(You may need to update `kubectl` for the previous command to work).
There are two paths to take here

## Configuring your Docker registry

Once you've decided which registries you will use and they are configured,
please make sure that the following list in `./config/clouddriver-local.yml` is
up to date.

```yaml
kubernetes:
  enabled: true
  accounts:
    - name: my-kubernetes-account
      dockerRegistries: # WARNING! only include configured accounts here
        - accountName: my-gcr-account
        - accountName: my-docker-account
```

### GCR

If you want to use Google Container Registry (GCR), you'll need to provide a
valid service account key to Spinnaker that has at least `read-only` access to
the storage buckets in your projects containing your Docker images.
[Here](https://cloud.google.com/iam/docs/service-accounts) is some background
reading on service accounts in GCP if this is unfamiliar to you.

Create the account with the necessary permissions:

```
gcloud iam service-accounts create \
    spinnaker-bootstrap-account \
    --display-name spinnaker-bootstrap-account

SA_EMAIL=$(gcloud iam service-accounts list \
    --filter="displayName:spinnaker-bootstrap-account" \
    --format='value(email)')

PROJECT=$(gcloud info --format='value(config.project)')

gcloud projects add-iam-policy-binding $PROJECT \
    --role roles/storage.admin --member serviceAccount:$SA_EMAIL
```

Download the key:

```
$ gcloud iam service-accounts keys create ~/.gcp/account.json \
    --iam-account $SA_EMAIL
```

Now in `./config/clouddriver-local.yml`, you'll find the following:

```yaml
dockerRegistry:
  enabled: true
  accounts:
    - name: my-gcr-account
      address: https://gcr.io
      username: _json_key
      passwordFile: /root/.kube/account.json
      repositories:
        - # the names of your GCR images (<project>/<image>)
```

In the above entry you need to update the `repositories` field to contain the
list of all images you want to index.

### Anything else... except for ECR

> ECR doesn't support enough of the [v2 registry
> api](https://docs.docker.com/registry/spec/api/) to be supportable by
> Spinnaker.


In `./config/clouddriver-local.yml`, you'll find the following:

```yaml
dockerRegistry:
  enabled: true
  accounts:
    - name: my-docker-account
      address: https://index.docker.io # Point to registry of choice
      username: # only supply if necessary
      password: # only supply if necessary
      repositories:
        - # names of docker images desired
```

Fill in the necessary field above, and you're all set. You can even have
multiple registries in separate accounts if you so choose, just make sure all
added Docker Registry account are linked to your Kubernetes account as
mentioned above.

## Configuring Pipeline Storage

Open `./config/spinnaker-local.yml`, where you'll find the below section which
is mostly filled out:

```yaml
  front50:
    host: spin-front50.spinnaker
    port: 8080
    baseUrl: ${services.default.protocol}://${services.front50.host}:${services.front50.port}

    # If using storage bucket persistence (gcs or s3), specify the bucket here
    # disable cassandra and enable the storage service below.
    storage_bucket: # Needs to be a globally unique name. Pick something clever.
    bucket_root: front50

    cassandra:
      enabled: false
    redis:
      enabled: false
    gcs:
      enabled: false # Enable me
      project: # Set me
    s3:
      enabled: false # Or me
```

Add values for `front50.storage_bucket`
(a unique bucket name that will created by spinnaker if it doesn't exist)
and set `true` for either `front50.gcs.enabled` or `front50.s3.enabled`
depending on which storage solution you want to use. If you
are using GCS, you need a json account file at `~/.gcp/account.json`, and
specify the project in `front50.gcs.project`.
Otherwise, you need your AWS credentials to be located at `~/.aws/credentials`.

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
containing Redis and Cassandra, and a `spin` application, containing all of the
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
mechanism (Redis), run

```
$ bash scripts/cleanup-spinnaker.sh # This deletes everything with application name spin
```
