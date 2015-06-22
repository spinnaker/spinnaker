'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.jenkins', [
  require('./jenkinsStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../caches/cacheInitializer.js'),
  require('../../../../caches/infrastructureCaches.js'),
  require('utils/timeFormatters.js'),
  require('../../../../jenkins/index.js'),
  require('./jenkinsExecutionDetails.controller.js'),
]).name;
