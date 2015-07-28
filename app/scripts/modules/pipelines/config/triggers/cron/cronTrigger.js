'use strict';

angular.module('spinnaker.pipelines.trigger.cron')
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'CRON',
      description: 'Executes the pipeline on a CRON schedule',
      key: 'cron',
      controller: 'CronTriggerCtrl',
      controllerAs: 'cronTriggerCtrl',
      templateUrl: 'scripts/modules/pipelines/config/triggers/cron/cronTrigger.html',
      popoverLabelUrl: 'scripts/modules/pipelines/config/triggers/cron/cronPopoverLabel.html'
    });
  })
  .controller('CronTriggerCtrl', function($scope, trigger, uuidService) {

    $scope.trigger = trigger;

    trigger.id = trigger.id || uuidService.generateUuid();

  });
