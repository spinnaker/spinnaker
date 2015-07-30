'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.findAmi', [
  require('./findAmiStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../account/account.module.js'),
  require('./findAmiExecutionDetails.controller.js'),
]).name;
