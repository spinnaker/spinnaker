'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.preconditions', [
  require('./preconditionTypeConfig.provider').name,
  require('./selector/preconditionSelector.directive').name,
  require('./preconditionList.directive').name,
  require('./preconditionType.service').name,
  require('./modal/editPrecondition.controller.modal').name,
  require('./precondition.details.filter').name,
]);
