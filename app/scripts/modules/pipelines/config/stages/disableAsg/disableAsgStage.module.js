'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.disableAsg', [
  require('./disableAsgStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../account/account.module.js'),
  require('./disableAsgExecutionDetails.controller.js'),
  require('utils/lodash.js'),
]).name;
