'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.quickPatchAsg.bulkQuickPatch', [
  require('./bulkQuickPatchStage.js'),
]);
