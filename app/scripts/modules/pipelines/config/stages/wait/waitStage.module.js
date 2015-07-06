'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.wait', [
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('./waitExecutionDetails.controller.js'),
]).name;
