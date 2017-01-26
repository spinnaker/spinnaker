# Project structure

This is a brief overview of how the project is structured.

## halyard-cli/

All CLI related code is here. 

 * `command.v1` defines the command structure and entrypoints into command 
   processing.  For example, the behavior of `hal config` is defined in the
   `com.netflix.spinnaker.halyard.cli.command.v1.ConfigCommand.java` class.
 
 * `ui.v1` defines UI output behavior & formatting.

## halyard-config/

The config validation & structure is defined here.

 * `config.v1` defines utilities for loading the halconfig.

 * `errors.v1` defines error & exception classes to be thrown by the
   validation.

 * `model.v1` defines the structure of the halconfig. The `providers`
   sub-package models the different 
   [clouddriver](https://github.com/spinnaker/clouddriver) provider's config
   sections, and is important if you want to add support for a specific
   provider's configuration options
 
 * `services.v1` defines classes for retrieving and updating different parts 
   of your halconfig. These are ultimately consumed by the web controllers.

 * `validate.v1` defines the validation behavior for our config. The
   classes described in the `providers` sub-package are used in annotations for
   specific fields/classes to allow only the necessary validators to run when
   required.

## halyard-web/

The web controllers & endpoints are defined here.

 * `controllers.v1` defines the set of endpoints the daemon is reachable from.

 * `errors.v1` catches validation exceptions and sends them to the client as
   JSON.
