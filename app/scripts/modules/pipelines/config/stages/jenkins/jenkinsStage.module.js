'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.jenkins', [
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../caches/cacheInitializer.js'),
  require('../../../../caches/infrastructureCaches.js'),
  require('../../../../utils/timeFormatters.js'),
  require('../../triggers/jenkins/igor.service.js'),
  require('./jenkinsExecutionDetails.controller.js'),
]);
