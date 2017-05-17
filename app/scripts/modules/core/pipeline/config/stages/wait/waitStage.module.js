'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.wait', [
  require('./waitStage.js'),
]);
