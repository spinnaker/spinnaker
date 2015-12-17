'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.jenkins', [
  require('./jenkinsStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../cache/cacheInitializer.js'),
  require('../../../../cache/infrastructureCaches.js'),
  require('../../../../utils/timeFormatters.js'),
  require('../../../../ci/jenkins/igor.service.js'),
  require('./jenkinsExecutionDetails.controller.js'),
]);
