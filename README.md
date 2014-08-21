Starting it up
==============

Prerequisites
-------------
Make sure the following are installed:

  * NodeJS and Node Package Manager, which is part of the NodeJS installation ([download](http://nodejs.org/download/))
  * Grunt, a JavaScript task runner (installed via NPM: ```npm install grunt```)


Quick Start
-----------
Run the following commands to get everything installed.
  
  * ```sudo npm install``` (You might not need to sudo this - but you _might need to sudo this_. If you try it without sudo and it fails, do a `rm -rf node_modules`, then try again with sudo)
  * ```grunt serve```

The app will start up on localhost:9000.

As you make changes locally, Grunt's livereload plugin watches your target directories and automatically updates
the running application, refreshing the page as changes are detected.
