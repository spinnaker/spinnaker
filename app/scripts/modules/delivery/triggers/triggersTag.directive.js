'use strict';

angular.module('spinnaker.delivery.execution.triggers', [
  'spinnaker.delivery.executions.service',
  'spinnaker.pipelines.config',
  'spinnaker.delivery.execution.triggers.toggle.modal.controller',
])
  .directive('triggersTag', function() {
    return {
      restrict: 'E',
      scope: {
        pipeline: '=',
      },
      templateUrl: 'scripts/modules/delivery/triggers/triggersTag.html',
      controller: 'TriggersTagCtrl',
      controllerAs: 'triggersTagCtrl',
    };
  })
  .controller('TriggersTagCtrl', function($scope, pipelineConfigService, pipelineConfig) {
    $scope.popover = {
      show: false,
    };


    this.getTriggerTemplateUrl = function(trigger) {
      return pipelineConfig.getTriggerConfig(trigger.type).popoverLabelUrl;
    };

    function updateTriggerInfo() {
      var pipeline = $scope.pipeline;
      if (pipeline && pipeline.triggers && pipeline.triggers.length) {
        var triggerCount = pipeline.triggers.length,
            activeTriggerCount = _.filter(pipeline.triggers, { enabled: true }).length,
            inactiveTriggerCoount = triggerCount - activeTriggerCount;
        $scope.triggerCount = triggerCount;
        $scope.activeTriggerCount = activeTriggerCount;
        if (triggerCount === 1) {
          if (activeTriggerCount) {
            $scope.triggerTooltip = 'This pipeline has an active trigger.';
          } else {
            $scope.triggerTooltip = 'This pipeline has a trigger, but it is currently disabled.';
          }
        } else {
          if (activeTriggerCount === 0) {
            $scope.triggerTooltip = 'All ' + triggerCount + ' triggers are disabled for this pipeline.';
          } else {
            $scope.triggerTooltip = 'This pipeline has multiple triggers:<br/>' +
              activeTriggerCount + ' enabled, ' +
              inactiveTriggerCoount + ' disabled.';
          }
        }
        $scope.triggerTooltip += '<br/><b>(click to manage)</b>';
      }
    }

    this.toggleTrigger = function(triggerIndex) {
      pipelineConfigService.toggleTrigger($scope.pipeline, triggerIndex).then(updateTriggerInfo);
    };

    updateTriggerInfo();
  });
