'use strict';

let angular = require('angular');

import {NetflixSettings} from '../../../netflix.settings';
import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

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
