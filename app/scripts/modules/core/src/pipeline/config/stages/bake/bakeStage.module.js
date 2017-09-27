'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.bake', [
  require('./bakeStage').name,
  require('./modal/addExtendedAttribute.controller.modal').name
]);
