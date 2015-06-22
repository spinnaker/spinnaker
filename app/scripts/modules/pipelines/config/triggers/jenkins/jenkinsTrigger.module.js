'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.trigger.jenkins', [
  require('../trigger.module.js'),
  require('restangular'),
  require('./igor.service.js'),
  require('../../../../caches/cacheInitializer.js'),
  require('../../../../caches/infrastructureCaches.js'),
  require('../../../../utils/timeFormatters.js'),
]);
