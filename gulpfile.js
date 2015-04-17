/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

'use strict';

var gulp = require('gulp'),
    $ = require('gulp-load-plugins')(),
    karma = require('karma'),
    del = require('del'),
    bowerFiles = require('main-bower-files'),
    run = require('run-sequence'),
    templateCache = require('gulp-angular-templatecache'),

    dist = 'dist',
    app = 'app',
    styles = 'styles',
    scripts = 'scripts',
    views = 'views',
    images = 'images',
    fonts = 'fonts',

    development = process.env.NODE_ENV === 'dev',
    release = process.env.NODE_ENV === 'release',

    min = $.htmlmin({
      collapseWhitespace: true,
      conservativeCollapse: true,
      collapseBooleanAttributes: true,
      removeCommentsFromCDATA: true,
      removeOptionalTags: true
    });

gulp.task('test:karma', function(done) {
  karma.server.start({
    configFile: __dirname+'/karma.conf.js',
    singleRun: true,
  }, done);
});

gulp.task('test:ci', function(done) {
  run(
    'clean',
    'build:prepare',
    function() {
      karma.server.start({
        configFile: __dirname+'/karma.conf.js',
        browsers: ['Chrome'],
        singleRun: false,
      }, done);
    }
  );
});

gulp.task('e2e', ['connect'], function() {
  return gulp.src(['test/e2e/**/*.js'])
    .pipe($.protractor.protractor({
      configFile: 'protractor.conf.js',
    }))
    .on('error', function(error) {
      console.log(error);
      process.exit();
    })
    .on('close', process.exit);
});

gulp.task('test', ['test:karma']);

gulp.task('clean:fonts', function(done) {
  del([dist, fonts].join('/'), done);
});

gulp.task('clean:images', function(done) {
  del([dist, images].join('/'), done);
});

// clean individual files by naming convention
['vendor', 'application'].forEach(function(item) {
  gulp.task('clean:'+styles+':'+item, function(done) {
    del([dist, styles, item+'*.css'].join('/'), done);
  });
});
['application', 'vendor', 'templates', 'settings', 'plugins'].forEach(function(item) {
  gulp.task('clean:'+scripts+':'+item, function(done) {
    del([
      [dist, scripts, item+'*.js'].join('/'),
      [dist, scripts, item+'*.js.map'].join('/'),
    ], done);
  });
});

gulp.task('clean', function(done) {
  del(dist, done);
});

// clean index.html
gulp.task('clean:html', function(done) {
  del([dist, 'index.html'].join('/'), done);
});

gulp.task('clean:static:bower', function(done) {
  del('dist/bower_components', done);
});

gulp.task('clean:static', function(done) {
  del([
    [dist, images].join('/'),
    [dist, '!(*.html)'].join('/'),
  ], done);
});

gulp.task('fonts', ['clean:fonts'], function() {
  return gulp.src([
    [app, fonts, '*/**'].join('/'),
    'bower_components/boostrap/dist/fonts/*.*',
  ]).pipe(gulp.dest([dist, fonts].join('/')));
});

gulp.task('images', ['clean:images'], function() {
  return gulp.src([
    [app, images, '*/**'].join('/'),
  ]).pipe(gulp.dest([dist, images].join('/')));
});

gulp.task('static:bower', ['clean:static:bower'], function() {
  return gulp.src('bower_components/**/*').pipe(gulp.dest(dist+'/bower_components'));
});

gulp.task('static', ['clean:static', 'static:bower'], function() {
  return gulp.src([
    [app, images, '*/**'].join('/'),
    [app, '!(*.html)'].join('/'),
    'bower_components/**/*',
  ]).pipe(gulp.dest(dist));
});

gulp.task('html', ['clean:html'], function() {
  return gulp.src([app, 'index.html'].join('/'))
    .pipe($.inject(gulp.src([
      [dist, styles, 'vendor*.css'].join('/'),
      [dist, styles, '**/*.css'].join('/'),
      [dist, fonts, '**/*.css'].join('/'),
    ], {read: false}), {ignorePath: dist}))
    .pipe($.inject(gulp.src([
      [dist, scripts, 'vendor*.js'].join('/'),
      [dist, scripts, 'templates*.js'].join('/'),
      [dist, scripts, 'application*.js'].join('/'),
    ], {read: false}), {ignorePath: dist}))
    .pipe(gulp.dest(dist));
});

var prepareCss = function(src, out) {
  return src.pipe($.sourcemaps.init({loadMaps: true}))
    .pipe($.less())
    .pipe($.concatCss(out))
    .pipe($.rev())
    .pipe($.if(release, $.cssmin()))
    // https://github.com/wearefractal/gulp-concat/issues/66
    //.pipe($.sourcemaps.write('.'))
    .pipe(gulp.dest([dist, styles].join('/')));
};
gulp.task('css:vendor', ['clean:styles:vendor'], function() {
  return prepareCss(gulp.src([
    './bower_components/bootstrap/dist/css/bootstrap.css',
    './bower_components/select2-bootstrap-css/select2-bootstrap.css',
    './bower_components/select2/select2.css',
    './bower_components/angular-wizard/dist/angular-wizard.css',
    './bower_components/angular-ui-select/dist/select.css',
    './bower_components/octicons/octicons/octicons.css'
  ], {base: 'styles/'}), 'vendor.css');
});
gulp.task('css:application', ['clean:styles:application'], function() {
  return prepareCss(gulp.src([
    [app, styles, '**/*.less'].join('/'),
    [app, scripts, '**/*.less'].join('/'),
  ]), 'application.css');
});
gulp.task('css', ['css:application', 'css:vendor']);

gulp.task('jshint', function() {
  return gulp.src([
    [app, scripts, '**/*.js'].join('/'),
    '!**/*.spec.js'
  ])
    .pipe($.if(!development, $.jshint()))
    .pipe($.if(!development, $.jshint.reporter('jshint-stylish')))
    .pipe($.if(!development, $.jshint.reporter('fail')));
});

var prepareJs = function(src, out) {
  return src.pipe($.if(!release, $.sourcemaps.init({loadMaps: true})))
    .pipe($.concat(out))
    .pipe($.rev())
    .pipe($.if(release, $.ngAnnotate()))
    .pipe($.if(release, $.uglify()))
    .pipe($.if(!release, $.sourcemaps.write('.')))
    .pipe(gulp.dest([dist, scripts].join('/')));
};
gulp.task('scripts:settings', ['clean:scripts:settings'], function() {
  return gulp.src([app, 'scripts/settings/settings.js'].join('/'))
    .pipe(gulp.dest([dist, scripts].join('/')));
});
gulp.task('scripts:application', ['jshint', 'clean:scripts:application'], function() {
  return prepareJs(gulp.src([
    [app, scripts, 'app.js'].join('/'),
    [app, scripts, 'modules/**/*.module.js'].join('/'),
    [app, scripts, 'providers/*.js'].join('/'),
    [app, scripts, '**/!(settings).js'].join('/'),
    '!**/*.spec.js',
  ]), 'application.js');
});
gulp.task('scripts:vendor', ['clean:scripts:vendor'], function() {
  return prepareJs(
    gulp.src([
      'bower_components/jquery/dist/jquery.js',
    ]
    .concat(bowerFiles({filter: /.*\.js/i}))
    .concat('bower_components/bootstrap/js/tooltip.js')), 'vendor.js');
});

gulp.task('scripts:templates', ['clean:scripts:templates'], function() {
  return gulp.src([
      [app, '**/*.html'].join('/'),
    ])
    .pipe($.if(release, min))
    .pipe(templateCache('templates.js', {
      standalone: true,
      module: 'deckApp.templates',
      root: '',
    }))
    .pipe($.rev())
    .pipe(gulp.dest([dist, scripts].join('/')));
});

gulp.task('scripts:plugins', ['clean:scripts:plugins'], function() {
  return gulp.src([app, 'scripts/plugins.js'].join('/'))
    .pipe(gulp.dest([dist, scripts].join('/')));
});

gulp.task('scripts', ['scripts:application', 'scripts:vendor', 'scripts:templates', 'scripts:settings', 'scripts:plugins']);

gulp.task('connect', function() {
  $.connect.server({
    root: [dist],
    port: 9000,
    livereload: true,
    host: '0.0.0.0',
  });
});

gulp.task('build:prepare', ['scripts', 'css', 'fonts', 'images', 'static']);
gulp.task('build', ['clean'], function(done) {
  if (development) {
    run('build:prepare', 'html', done);
  } else {
    run('build:prepare', 'test:karma', 'html', done);
  }
});

gulp.task('serve:prepare', ['build']);
gulp.task('serve', ['serve:prepare', 'connect', 'watch']);

gulp.task('default', ['serve']);

gulp.task('watch', function() {
  gulp.watch('./app/**/*.html', function() {
    run('scripts:templates', 'html');
  });
  gulp.watch('./app/**/*.less', function() {
    run('css:application', 'html');
  });
  gulp.watch('./app/fonts/**/*.css', function() {
    run('fonts', 'html');
  });
  gulp.watch('./app/scripts/**/*.js', function() {
    run('scripts:application', 'html', 'test:karma');
  });
  gulp.watch('./bower_components/**/*.js', function() {
    run('scripts:vendor', 'html');
  });
  gulp.watch('./bower_components/**/*.css', function() {
    run('css:vendor', 'html');
  });
  gulp.watch('./app/scripts/settings/settings.js', function() {
    run('scripts:settings');
  });
  if (!development) {
    gulp.watch(['./test/**/*','./app/**/*.spec.js'], function() {
      run('test:karma');
    });
  }
  $.watch([
    'dist/index.html'
  ]).pipe($.connect.reload());
});

