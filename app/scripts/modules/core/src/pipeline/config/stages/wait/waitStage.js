'use strict';

const angular = require('angular');

import {SKIP_WAIT_COMPONENT} from './skipWait.component';
import {WaitExecutionLabel} from './WaitExecutionLabel';

module.exports = angular.module('spinnaker.core.pipeline.stage.waitStage', [
  SKIP_WAIT_COMPONENT,
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      label: 'Wait',
      description: 'Waits a specified period of time',
      key: 'wait',
      templateUrl: require('./waitStage.html'),
      executionDetailsUrl: require('./waitExecutionDetails.html'),
      executionConfigSections: ['waitConfig', 'taskStatus'],
      executionLabelComponent: WaitExecutionLabel,
      useCustomTooltip: true,
      strategy: true,
      controller: 'WaitStageCtrl',
      validators: [
        { type: 'requiredField', fieldName: 'waitTime' },
      ],
    });
  }).controller('WaitStageCtrl', function ($scope, stage) {
    if (!stage.waitTime) {
      stage.waitTime = 30;
    }
  });
