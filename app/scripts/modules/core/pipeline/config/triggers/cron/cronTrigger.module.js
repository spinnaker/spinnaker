'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.trigger.cron', [
    require('../trigger.directive.js'),
    require('../../../../../utils/uuid.service.js'),
    require('./cron.validator.directive.js'),
  ])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'CRON',
      description: 'Executes the pipeline on a CRON schedule',
      key: 'cron',
      controller: 'CronTriggerCtrl',
      controllerAs: 'cronTriggerCtrl',
      templateUrl: 'app/scripts/modules/pipelines/config/triggers/cron/cronTrigger.html',
      popoverLabelUrl: 'app/scripts/modules/pipelines/config/triggers/cron/cronPopoverLabel.html'
    });
  })
  .controller('CronTriggerCtrl', function($scope, trigger, uuidService) {

    $scope.trigger = trigger;

    trigger.id = trigger.id || uuidService.generateUuid();

  })
.name;
