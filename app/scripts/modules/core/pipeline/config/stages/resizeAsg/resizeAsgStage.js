'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.resizeAsgStage', [
  require('../../pipelineConfigProvider.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'resizeServerGroup',
      label: 'Resize Server Group',
      description: 'Resizes a server group',
      strategy: true,
    });
  });

