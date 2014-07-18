Starting it up
==============

Prerequisites
-------------
Make sure the following are installed - to be safe, you might want to run all these commands in sudoku mode (`sudo [command]`):

  * NodeJS and Node Package Manager, which is part of the NodeJS installation ([download](http://nodejs.org/download/))
  * Bower, a JS package manager (installed via NPM: ```npm install -g bower```)
  * Grunt, a JavaScript task runner (installed via NPM: ```npm install grunt```)


Quick Start
-----------
Run the following commands to get everything installed.
  
  * ```sudo npm install``` (You might not need sudoku mode - but you _might need sudoku mode_. If you try it without `sudo` and it fails, do a `rm -rf node_modules`, then try again with `sudo`)
  * ```bower install```
  * ```grunt serve```

The app will start up on localhost:9000, which is pretty incredible.

As you make changes locally, Grunt's livereload plugin watches your target directories and automatically updates
the running application, refreshing the page as changes are detected.
