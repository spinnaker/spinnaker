'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.netflix.pipeline.stage.chap', [
  require('core/pipeline/config/pipelineConfigProvider.js'),
  require('./chapStage.controller'),
  require('./chapExecutionDetails.controller'),
  require('core/config/settings.js'),
]).config(function (pipelineConfigProvider, settings) {
  if (settings.feature && settings.feature.netflixMode && settings.chap) {
    pipelineConfigProvider.registerStage({
      label: 'ChAP',
      description: 'Run a ChAP test case',
      extendedDescription: `<a target="_blank" href="${settings.chap.chapBaseUrl}">
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
