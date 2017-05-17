'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.quickPatchAsg', [
  require('./quickPatchAsgStage.js'),
]);
