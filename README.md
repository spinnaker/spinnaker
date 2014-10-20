Starting it up
==============

Prerequisites
-------------
Make sure the following are installed on your system:

  * NodeJS and Node Package Manager, which is part of the NodeJS installation ([download](http://nodejs.org/download/))
  * the Gulp build system (installed via NPM: ```npm install gulp -g```)
  
Note: if you're on a Mac, you will want to use node 0.10.7; there are some issues with 0.10.8/9/10 that may prevent
 PhantomJS from running. If you try to start the application via the `gulp` command and you see PhantomJS hang with a 
 message "A browser has connected on socket", you'll want to downgrade. From the command line, simply type `n 0.10.7`
 and you'll be set.


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
