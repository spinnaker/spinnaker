'use strict';

const angular = require('angular');

import { UUIDGenerator } from 'core/utils/uuid.service';
import { Registry } from 'core/registry';
import { ServiceAccountReader } from 'core/serviceAccount/ServiceAccountReader';
import { SETTINGS } from 'core/config/settings';

import './cronTrigger.less';

module.exports = angular
  .module('spinnaker.core.pipeline.trigger.cron', [
    require('angular-cron-gen'),
    require('../trigger.directive').name,
    require('./cron.validator.directive').name,
  ])
  .config(function() {
    Registry.pipeline.registerTrigger({
      label: 'CRON',
      description: 'Executes the pipeline on a CRON schedule',
      key: 'cron',
      controller: 'CronTriggerCtrl',
      controllerAs: 'vm',
      templateUrl: require('./cronTrigger.html'),
      executionTriggerLabel: trigger => trigger.cronExpression,
      validators: [
        {
          type: 'serviceAccountAccess',
          message: `You do not have access to the service account configured in this pipeline's CRON trigger.
                    You will not be able to save your edits to this pipeline.`,
          preventSave: true,
        },
      ],
    });
  })
  .controller('CronTriggerCtrl', function(trigger) {
    this.trigger = trigger;
    this.fiatEnabled = SETTINGS.feature.fiatEnabled;

    ServiceAccountReader.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });

    this.validationMessages = {};

    trigger.id = trigger.id || UUIDGenerator.generateUuid();
    trigger.cronExpression = trigger.cronExpression || '0 0 10 ? * MON-FRI *';

    this.cronOptions = {
      formSelectClass: 'form-control input-sm',
      hideAdvancedTab: false,
      hideSeconds: true,
      use24HourTime: true,
    };
  })
  .run($templateCache =>
    $templateCache.put('spinnaker-custom-cron-picker-template', $templateCache.get(require('./cronPicker.html'))),
  );
