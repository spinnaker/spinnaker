'use strict';

let angular = require('angular');

//BEN_TODO: where is this defined?

module.exports = angular.module('spinnaker.core.pipeline.stage.destroyAsgStage', [
  require('../../../../utils/lodash.js'),
  require('../stageConstants.js'),
  require('../../pipelineConfigProvider.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'destroyServerGroup',
      label: 'Destroy Server Group',
      description: 'Destroys a server group',
      strategy: true,
    });
  });
