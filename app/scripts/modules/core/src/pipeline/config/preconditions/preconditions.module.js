'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.config.preconditions', [
  require('./preconditionTypeConfig.provider.js').name,
  require('./selector/preconditionSelector.directive.js').name,
  require('./preconditionList.directive.js').name,
  require('./preconditionType.service.js').name,
  require('./modal/editPrecondition.controller.modal.js').name,
  require('./precondition.details.filter.js').name
]);
