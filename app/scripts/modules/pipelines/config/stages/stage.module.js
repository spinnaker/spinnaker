'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage', [
  require('../../pipelines.module.js'),
  require('./stage.js'),
  require('./stageConstants.js'),

]);
