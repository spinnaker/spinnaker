'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.bake', [
  require('./bakeStage'),
  require('./modal/addExtendedAttribute.controller.modal')
]);
