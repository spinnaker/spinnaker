Starting it up
==============

Prerequisites
-------------
Make sure the following are installed on your system:

  * NodeJS and Node Package Manager, which is part of the NodeJS installation ([download](http://nodejs.org/download/))
  * the Gulp build system (installed via NPM: ```npm install gulp -g```)


Quick Start
-----------
Run the following commands (in the deck directory) to get all dependencies installed in deck and to start the server:
  
  * ```npm install```
  * ```gulp```

The app will start up on localhost:9000.

As you make changes locally, Gulp's livereload plugin watches your target directories and automatically updates
the running application, refreshing the page as changes are detected.

Silencing jshint
----------------
To prevent jshint from running, run gulp with ```NODE_ENV=dev gulp```.
