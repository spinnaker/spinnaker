'use strict';

const angular = require('angular');

import { PIPELINE_CONFIG_PROVIDER } from '@spinnaker/core';

import { NetflixSettings } from 'netflix/netflix.settings';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.chap', [
  PIPELINE_CONFIG_PROVIDER,
  require('./chapStage.controller'),
  require('./chapExecutionDetails.controller')
]).config(function (pipelineConfigProvider) {
  if (NetflixSettings.feature.netflixMode && NetflixSettings.chap) {
    pipelineConfigProvider.registerStage({
      label: 'ChAP',
      description: 'Run a ChAP test case',
      extendedDescription: `<a target="_blank" href="${NetflixSettings.chap.chapBaseUrl}">
          <span class="small glyphicon glyphicon-file"></span> Documentation</a>`,
      key: 'chap',
      controller: 'ChapStageCtrl',
      controllerAs: 'stageCtrl',
      templateUrl: require('./chapStage.html'),
      executionDetailsUrl: require('./chapExecutionDetails.html'),
      validators: [
      ],
    });
  }
});
