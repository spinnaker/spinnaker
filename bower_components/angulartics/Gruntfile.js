module.exports = function(grunt) {
   'use strict';

   grunt.initConfig({
      pkg: grunt.file.readJSON('package.json'),
      bower: grunt.file.readJSON('bower.json'),

      shell: {
         publish: {
            command: 'npm publish'
         }
      },

      bump: {
         options: {
            files: ['package.json', 'bower.json'],
            updateConfigs: ['pkg', 'bower'],
            commit: true,
            commitMessage: 'Release v%VERSION%',
            commitFiles: ['package.json'],
            createTag: true,
            tagName: 'v%VERSION%',
            tagMessage: 'Version %VERSION%',
            push: true,
            pushTo: 'upstream',
            gitDescribeOptions: '--tags --always --abbrev=1 --dirty=-d'
         }
      },

      karma: {
         unit: {
            configFile: 'karma.conf.js',
            singleRun: true
         }
      },

      jshint: {
         all: ['Gruntfile.js', 'src/*.js', 'test/**/*.js']
      },

      concat: {
         options: {
            stripBanners: false
         },
         dist: {
            src: ['dist/angulartics-scroll.min.js', 'components/jquery-waypoints/waypoints.min.js'],
            dest: 'dist/angulartics-scroll.min.js'
         }
      },

      uglify: {
         options: {
            preserveComments: 'some',
            report: 'min'
         },
         predist: {
            files: {
               'dist/angulartics-scroll.min.js': ['src/angulartics-scroll.js']
            }
         },
         dist: {
            files: [{
               expand: true,
               cwd: 'src/',
               src: ['*.js', '!angulartics-scroll.js'],
               dest: 'dist/',
               ext: '.min.js'
            }]
         }
      },

      clean: ['dist']
   });

   require('load-grunt-tasks')(grunt);

   grunt.registerTask('test', ['jshint', 'karma']);
   grunt.registerTask('default', ['jshint', 'karma', 'uglify:predist', 'concat:dist', 'uglify:dist']);

   grunt.registerTask('release', 'Release a new version, push it and publish it', function(target) {
     if (!target) {
       target = 'patch';
     }
     return grunt.task.run('bump-only:' + target, 'default', 'bump-commit', 'shell:publish');
   });

};
