'use strict';

let angular = require('angular');

import {UUIDGenerator} from 'core/utils/uuid.service';
import {SERVICE_ACCOUNT_SERVICE} from 'core/serviceAccount/serviceAccount.service.ts';
import {SETTINGS} from 'core/config/settings';
import './cronTrigger.less';

module.exports = angular.module('spinnaker.core.pipeline.trigger.cron', [
    require('angular-cron-gen'),
    require('../trigger.directive.js'),
    SERVICE_ACCOUNT_SERVICE,
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
      validators: [
        {
          type: 'serviceAccountAccess',
          message: `You do not have access to the service account configured in this pipeline's CRON trigger.
                    You will not be able to save your edits to this pipeline.`,
          preventSave: true,
        }
      ]
    });
  })
  .controller('CronTriggerCtrl', function(trigger, serviceAccountService) {

    this.trigger = trigger;
    this.fiatEnabled = SETTINGS.feature.fiatEnabled;

    serviceAccountService.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });

    this.validationMessages = {};

    trigger.id = trigger.id || UUIDGenerator.generateUuid();
    trigger.cronExpression = trigger.cronExpression || '0 0 10 ? * MON-FRI *';

    this.cronOptions = {
      formSelectClass: 'form-control input-sm',
      hideAdvancedTab: false,
      hideSeconds: true,
      use24HourTime: true
    };

  }).run($templateCache => $templateCache.put('spinnaker-custom-cron-picker-template', $templateCache.get(require('./cronPicker.html'))));
