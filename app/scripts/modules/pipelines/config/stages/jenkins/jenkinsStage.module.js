'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.jenkins', [
  require('./jenkinsStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../core/cache/cacheInitializer.js'),
  require('../../../../core/cache/infrastructureCaches.js'),
  require('../../../../utils/timeFormatters.js'),
  require('../../../../jenkins/igor.service.js'),
  require('./jenkinsExecutionDetails.controller.js'),
]).name;
