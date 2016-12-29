'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.disableAsgStage', [
  require('../../pipelineConfigProvider.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'disableServerGroup',
      label: 'Disable Server Group',
      description: 'Disables a server group',
      strategy: true,
    });
  });
