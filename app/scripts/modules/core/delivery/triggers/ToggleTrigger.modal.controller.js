'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.execution.triggers.toggle.modal.controller', [
  require('../../pipeline/config/services/pipelineConfigService.js'),
])
  .controller('ToggleTriggerModalCtrl', function($scope, pipeline, trigger, $modalInstance, pipelineConfig, pipelineConfigService) {

    this.cancel = $modalInstance.dismiss;

    $scope.pipeline = pipeline;
    $scope.trigger = trigger;

    $scope.triggerLabelUrl = pipelineConfig.getTriggerConfig(trigger.type).popoverLabelUrl;

    $scope.action = trigger.enabled ? 'Disable' : 'Enable';

    this.toggleTrigger = function() {
      trigger.enabled = !trigger.enabled;
      pipelineConfigService.savePipeline(pipeline).then($modalInstance.close);
    };

  });
