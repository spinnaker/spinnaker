module.exports = function(grunt){
  'use strict';

  grunt.initConfig({
    pkg: grunt.file.readJSON('bower.json'),

    jshint: {
      options: { jshintrc: true, force: true },
      all: ['gruntfile.js', '<%= pkg.name %>.js']
    },

    bump: {
      options: {
        files: ['bower.json','package.json'],
        commit: true,
        commitMessage: 'release %VERSION%',
        commitFiles: ['package.json','bower.json','<%= pkg.name %>.min.js'],
        pushTo: 'origin',
      }
    },

    uglify: {
      options: {
        banner: '/*\n * <%= pkg.title || pkg.name %> <%= pkg.version %>\n' +
          ' * (c) <%= grunt.template.today("yyyy") %> <%= pkg.authors.join(" ") %>\n' +
          ' * Licensed <%= pkg.license %>\n */\n'
      },
      src: {
        files: {
          '<%= pkg.name %>.min.js': '<%= pkg.name %>.js'
        }
      }
    },

    karma: {
      unit: {
        configFile: 'karma.conf.js'
      },
      once: {
        configFile: 'karma.conf.js',
        singleRun: true,
        browsers: ['PhantomJS']
      },
      server: {
        configFile: 'karma.conf.js',
        singleRun: false,
        autoWatch: true,
        browsers: ['PhantomJS']
      }
    },

    'gh-pages': {
      options: {
        base: 'docs'
      },
      src: ['**']
    },

    ngdocs: {
      options: {
        html5Mode: false,
        titleLink: "#/api",
        navTemplate: './docs-template/nav.html',
        scripts: [
          'angular.js',
          './bower_components/marked/lib/marked.js',
          './<%= pkg.name %>.js',
          './docs-template/script.js',
        ],
        discussions: {
          shortName: 'hypercubedgithub',
          url: 'http://hypercubed.github.io/angular-marked/',
          dev: false
        }
      },
      all: ['<%= pkg.name %>.js']
    },

    connect: {
      server: {
        options: {
          port: 9001,
          base: 'docs',
          hostname: 'localhost',
          open: true
        }
      }
    },

    watch: {
      parser: {
        files: ['<%= pkg.name %>.js','./docs-template/*.*'],
        tasks: ['build']
      }
    }

  });

  require('load-grunt-tasks')(grunt);

  grunt.registerTask('serve', ['build','connect','watch']);

  grunt.registerTask('default', ['test', 'build']);
  grunt.registerTask('build', ['jshint', 'uglify', 'ngdocs']);
  grunt.registerTask('test', ['karma:once']);
  grunt.registerTask('publish', ['test','bump-only','uglify','bump-commit','gh-pages']);

};
