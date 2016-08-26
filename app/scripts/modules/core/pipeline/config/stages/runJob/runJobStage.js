'use strict';

let angular = require('angular');

//BEN_TODO
module.exports = angular.module('spinnaker.core.pipeline.stage.runJobStage', [
  require('../../pipelineConfigProvider.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'runJob',
      label: 'Run Job',
      description: 'Runs a container'
    });
  });

