# Consul & Spinnaker

> _This is a work in progress, and not complete yet. Please hold off on using
> this guide until this message disappears_

> _Currently only the Google Spinnaker provider supports Consul. If you're
> interested in adding support for another platform, ping @lwander_

There are two ways to use [Consul](consul.io) + Spinnaker. The first assumes
you don't have Consul running in an existing deployment, and the second assumes
you do. If you fall in the first bucket, continue reading.

## Configuring Consul From Scratch

This assumes you're not already using Consul for service discovery. This serves
as a cursory guide, and assumes you're familiar with the fundamentals of
Consul. If not, [try the
tutorial](https://www.consul.io/intro/getting-started/install.html).

### Installing Consul

If you're using Consul, Spinnaker assumes that you've taken care of
installing the Consul agent on each machine you want to be discoverable. The
best way to take care of this is to create a base image with Consul installed,
and install further packages on top of that. To do so, copy this directory
onto a VM, run `$ sudo bash install/install.sh [client|server]`, and capture 
the resulting disk [as an
image](https://cloud.google.com/compute/docs/images/create-delete-deprecate-private-images). 
There are many ways to do this (Packer, config-management, etc...) and we'll 
leave that up to you.

> __IMPORTANT__ For spinnaker to communicate with and join Consul nodes to the
> network, they must provide the `-client` flag to Consul with an
> address that's reachable from the machine running Spinnaker. This is taken 
> care of in the installation path provided here.

## 1. Starting your Consul Server

For Consul to work, each Consul Datacenter needs a set of server nodes, with
one acting as the leader. There are a few ways to get the Consul server nodes 
set up, and they are detailed
[here](https://www.consul.io/docs/guides/bootstrapping.html).

For an easy startup path, take the base image produced by running `$ sudo bash 
install.sh server` as described above, and start a 3 node cluster. 
Once this cluster is running with nodes `$NODE1, $NODE2, $NODE3`, run 
`$ consul join $NODE2 $NODE3` on `$NODE1`.

## 2. Starting your Consul Agents

We recommend having both the Consul binary, and a corresponding `/etc/init/`
entry on whatever base image you use to produce application images.

As an example, you can capture the image produced by running `$ sudo bash
install.sh client`.

## 3. Configuring Spinnaker

The easiest way to allow Spinnaker to communicate with Consul is to run a
Consul agent on whichever machine is running Clouddriver. The rational is that
this agent will always be able to keep track of the full list of Consul nodes,
providing Clouddriver with a fixed endpoint to query. Now all that's needed in
`clouddriver.yml` is the highlighted section below

```yaml
google:
  enabled: true
  accounts:
   - name: my-google-account
     consul:                         # # # # # # # # # # # # # # 
       enabled: true                 # This is the new section #
       agentEndpoint: localhost      # # # # # # # # # # # # # #
     jsonPath:  # ... configure the rest as you would normally
```
