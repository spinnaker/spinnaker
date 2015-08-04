Spinnaker UI
============

Prerequisites
-------------
Make sure that node and npm are installed on your system ([download](http://nodejs.org/download/)). The minimum versions for each are listed in package.json.

Quick Start
-----------
Run the following commands (in the deck directory) to get all dependencies installed in deck and to start the server:
  
  * ```npm install```
  * ```npm start```

The app will start up on localhost:9000.

Environment variables
---------------------
Environment variables can be used to configure application behavior. The following lists those variables and their possible values:

  * ```AUTH``` enable/disable authentication (default is disabled, enable by setting ```AUTH=enabled```).
  * ```CANARY``` enable/disable canary (default is enabled, disable by setting ```CANARY=disabled```).

The following external resources can be specified with environment variables:

  * ```FEEDBACK_URL``` overrides the default feedback posting url.
  * ```API_HOST``` overrides the default Spinnaker API host.

For example, ```API_HOST=spinnaker.prod.netflix.net npm start``` will run service with ```spinnaker.prod.netflix.net``` as the API host.

Testing
-------
To run the tests within the application, run ```npm test```. 

Building & Deploying
--------------------
To build the application, run ```npm run build```. The built application lives in ```dist/```.
