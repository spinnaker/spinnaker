'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.tagImage', [
  require('../stage.module.js').name,
  require('../core/stage.core.module.js').name,
  require('./tagImageStage.js').name,
]);
