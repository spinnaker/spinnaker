'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.enableAsgStage', [
  require('../../pipelineConfigProvider.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'enableServerGroup',
      label: 'Enable Server Group',
      description: 'Enables a server group',
      strategy: true,
    });
  });
