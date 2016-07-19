# Consul & Spinnaker

> _This is a work in progress, and not complete yet. Please hold off on using
> this guide until this message disappears_

There are two ways to use Consul + Spinnaker. The first assumes you don't have
Consul running in an existing deployment, and the second assumes you do. If you
fall in the first bucket, continue reading.

## Configuring Consul From Scratch

This assumes you're not already using Consul for service discovery.

### Installing Consul

If you're using [Consul](consul.io), Spinnaker assumes that you've taken care of 
installing the Consul agent on each machine you want to be discoverable. The 
best way to take care of this is to create a base image with Consul installed,
and install further packages on top of that. To do so, copy the file in
`./install/install.sh` onto a VM, run `$ sudo install.sh`, and capture the
resulting disk as a VM. There are many ways to do this (Packer,
config-management, etc...) and we'll leave that up to you.

## 2. Starting your Consul Server

_Details how to bootstrap your cluster_

## 3. Starting your Consul Agents

_Details how to start your agents_
