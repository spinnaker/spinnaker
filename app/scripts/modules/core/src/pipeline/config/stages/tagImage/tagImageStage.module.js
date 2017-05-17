'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.tagImage', [
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('./tagImageStage.js'),
]);
