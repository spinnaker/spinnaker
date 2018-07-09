# Codelabs

> This directory is under active development.

The goal is to make two things easy:

1. The setup & provisioning of Spinnaker codelabs for users, making our
   codelabs more of a "point & click" experience than an arduous "set
   everything up yourself" experience.

2. The creation of new codelabs, by providing a shared set of base installation
   scripts & utilities.

Take the [GKE source to
prod](https://codelabs.developers.google.com/codelabs/cloud-spinnaker-kubernetes-cd/)
codelab as an example.

## Publishing changes

Changes to codelabs need to be published by running the `./publish` script in
the base codelab directory. If you do not have permission to run this script
(it will fail) ping a user who made a prior change in this directory, or open
an [issue](https://github.com/spinnaker/spinnaker/issues).

## Creating a new codelab

> TODO(lwander)
