'use strict';

angular.module('deckApp.delivery', [

  'deckApp.delivery.executionGroupHeading.controller',
  'deckApp.delivery.pipelineExecutions.controller',
  'deckApp.delivery.execution.controller',
  'deckApp.delivery.executionBar.controller',
  'deckApp.delivery.executionGroup.controller',
  'deckApp.delivery.executionStatus.controller',

  'deckApp.delivery.executionGroups.filter',
  'deckApp.delivery.stages.filter',
  'deckApp.delivery.statusNames.filter',
  'deckApp.delivery.executions.filter',
  'deckApp.delivery.buildDisplayName.filter',

  'deckApp.delivery.execution.directive',
  'deckApp.delivery.executionBar.directive',
  'deckApp.delivery.executionDetails.directive',
  'deckApp.delivery.executionGroup.directive',
  'deckApp.delivery.executionGroupHeading.directive',
  'deckApp.delivery.executionStatus.directive',
  'deckApp.delivery.executionDetails.stageFailureMessage.directive',
  'deckApp.delivery.manualPipelineExecution.controller',

  'deckApp.executionDetails.controller',
  'deckApp.settings',
  'deckApp.utils.appendTransform',
  'deckApp.utils.d3',
  'deckApp.utils.lodash',
  'deckApp.utils.moment',
  'deckApp.utils.rx',
  'deckApp.utils.scrollTo',
  'deckApp.orchestratedItem.service',
]);
