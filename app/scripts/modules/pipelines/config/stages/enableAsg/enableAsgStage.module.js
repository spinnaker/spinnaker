'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.enableAsg', [
  require('./enableAsgStage.js'),
  require('./enableAsgExecutionDetails.controller.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../account/account.module.js'),
  require('../destroyAsg/destroyAsgExecutionDetails.controller.js'),
  require('utils/lodash.js'),
]).name;
