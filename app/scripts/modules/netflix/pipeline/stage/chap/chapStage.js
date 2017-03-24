'use strict';

let angular = require('angular');

import {NetflixSettings} from '../../../netflix.settings';

module.exports = angular.module('spinnaker.netflix.pipeline.stage.chap', [
  require('core/pipeline/config/pipelineConfigProvider.js'),
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
