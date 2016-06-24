'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.cloneServerGroupStage', [
  require('../../pipelineConfigProvider.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'cloneServerGroup',
      label: 'Clone Server Group',
      description: 'Clones a server group',
      strategy: true,
    });
  });

