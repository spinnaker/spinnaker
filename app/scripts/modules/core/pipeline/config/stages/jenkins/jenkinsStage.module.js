'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.jenkins', [
  require('./jenkinsStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('core/cache/cacheInitializer.js'),
  require('core/cache/infrastructureCaches.js'),
  require('core/utils/timeFormatters.js'),
  require('core/ci/jenkins/igor.service.js'),
  require('./jenkinsExecutionDetails.controller.js'),
]);
