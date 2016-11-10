'use strict';

let angular = require('angular');

import {UUID_SERVICE} from 'core/utils/uuid.service';

module.exports = angular.module('spinnaker.core.pipeline.trigger.cron', [
    require('../trigger.directive.js'),
    require('core/serviceAccount/serviceAccount.service.js'),
    UUID_SERVICE,
    require('./cron.validator.directive.js'),
  ])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerTrigger({
      label: 'CRON',
      description: 'Executes the pipeline on a CRON schedule',
      key: 'cron',
      controller: 'CronTriggerCtrl',
      controllerAs: 'vm',
      templateUrl: require('./cronTrigger.html'),
      popoverLabelUrl: require('./cronPopoverLabel.html'),
    });
  })
  .controller('CronTriggerCtrl', function(trigger, uuidService, settings, serviceAccountService) {

    this.trigger = trigger;
    this.fiatEnabled = settings.feature.fiatEnabled;

    serviceAccountService.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });

    this.validationMessages = {};

    trigger.id = trigger.id || uuidService.generateUuid();

  });
