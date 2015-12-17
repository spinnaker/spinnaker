'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.bake', [
  require('./bakeStage.js'),
  require('../stage.module.js'),
  require('../core/stage.core.module.js'),
  require('../../../../utils/lodash.js'),
]);
