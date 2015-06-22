Starting it up
==============

Prerequisites
-------------
Make sure the following are installed on your system:

  * NodeJS and Node Package Manager, which is part of the NodeJS installation ([download](http://nodejs.org/download/))


Quick Start
-----------
Run the following commands (in the deck directory) to get all dependencies installed in deck and to start the server:
  
  * ```npm install```
  * ```npm start```

The app will start up on localhost:8080.

e2e tests
---------
To run e2e tests, first make sure protractor is installed and webdriver is running:

  * ```npm install -g protractor```
  * ```webdriver-manager update```
  * ```webdriver-manager start```

Then, run the test suite via ```gulp test:e2e```.
