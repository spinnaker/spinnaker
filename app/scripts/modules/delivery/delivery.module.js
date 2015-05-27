'use strict';

angular.module('spinnaker.delivery', [

  'spinnaker.delivery.executionGroupHeading.controller',
  'spinnaker.delivery.pipelineExecutions.controller',
  'spinnaker.delivery.execution.controller',
  'spinnaker.delivery.executionBar.controller',
  'spinnaker.delivery.executionGroup.controller',
  'spinnaker.delivery.executionStatus.controller',

  'spinnaker.delivery.executionGroups.filter',
  'spinnaker.delivery.stages.filter',
  'spinnaker.delivery.statusNames.filter',
  'spinnaker.delivery.executions.filter',
  'spinnaker.delivery.buildDisplayName.filter',

  'spinnaker.delivery.execution.directive',
  'spinnaker.delivery.executionBar.directive',
  'spinnaker.delivery.executionDetails.directive',
  'spinnaker.delivery.executionGroup.directive',
  'spinnaker.delivery.executionGroupHeading.directive',
  'spinnaker.delivery.executionStatus.directive',
  'spinnaker.delivery.executionDetails.stageFailureMessage.directive',
  'spinnaker.delivery.manualPipelineExecution.controller',

  'spinnaker.executionDetails.controller',
  'spinnaker.settings',
  'spinnaker.utils.appendTransform',
  'spinnaker.utils.d3',
  'spinnaker.utils.lodash',
  'spinnaker.utils.moment',
  'spinnaker.utils.rx',
  'spinnaker.utils.scrollTo',
  'spinnaker.orchestratedItem.service',
]);
