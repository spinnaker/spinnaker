'use strict';

const angular = require('angular');

export const CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONS_MODULE = 'spinnaker.core.pipeline.config.preconditions';
export const name = CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONS_MODULE; // for backwards compatibility
angular.module(CORE_PIPELINE_CONFIG_PRECONDITIONS_PRECONDITIONS_MODULE, [
  require('./preconditionTypeConfig.provider').name,
  require('./selector/preconditionSelector.directive').name,
  require('./preconditionList.directive').name,
  require('./preconditionType.service').name,
  require('./modal/editPrecondition.controller.modal').name,
  require('./precondition.details.filter').name,
]);
