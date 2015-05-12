'use strict';

angular.module('deckApp.delivery.execution.triggers.toggle.modal.controller', [
  'deckApp.pipelines.config.service',
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
