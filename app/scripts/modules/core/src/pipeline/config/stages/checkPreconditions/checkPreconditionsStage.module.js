'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.checkPreconditions', [
  require('../stage.module.js').name,
  require('../core/stage.core.module.js').name,
  require('./checkPreconditionsStage.js').name,
]);
