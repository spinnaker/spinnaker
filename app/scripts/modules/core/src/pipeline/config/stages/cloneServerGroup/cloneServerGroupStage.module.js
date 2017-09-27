'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.cloneServerGroup', [
  require('./cloneServerGroupStage.js').name,
  require('../stage.module.js').name,
  require('../core/stage.core.module.js').name,
]);
