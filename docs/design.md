# Haylard

```
  _           _                     _
 | |__   __ _| |_   _  __ _ _ __ __| |
 | '_ \ / _` | | | | |/ _` | '__/ _` |
 | | | | (_| | | |_| | (_| | | | (_| |
 |_| |_|\__,_|_|\__, |\__,_|_|  \__,_|
                |___/

```

## Goals

The goal of Halyard is to make
[Spinnaker](https://github.com/spinnaker/spinnaker) easier to setup and operate
for non-trivial installations. Depending on the setup, there are more than ten
services to manage, all of which with their own configuration files. There is
no clear documentation of which services can be scaled horizontally, or which
service to scale for best performance. Updating stateful services such as
[Orca](https://github.com/spinnaker/orca) has its own set of challenges which
aren't documented and require hand-work to do correctly. Running Spinnaker on
multiple machines has no proper documentation, and isn't easy to do either. To
begin to address this, I want to list some goals:

1. Halyard should be able to create an instance of Spinnaker able to deploy to
   any number of providers without having the user edit any `.yaml` files.

2. If the user does want to make custom Spinnaker config changes, Halyard
   should enable by never overwriting their config.

3. A developer should be able to add a new config option to Halyard without
   having to modify the Halyard source code, barring special circumstances
   (more on this later). Ultimately, Halyard will be the place all Spinnaker
   config is stored.

4. Halyard should be able to deploy/monitor Spinnaker in a number of
   environments, e.g. Kubernetes, EC2, GCE, etc...

5. Halyard should update Spinnaker components seamlessly as long as the
   environment Spinnaker is installed to supports it.

6. Versions of the different Spinnaker services should be pinned by Halyard,
   and updated in Halyard.

## Design

## Interface
