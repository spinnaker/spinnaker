'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.pipeline.stage.disableAsgStage', [
  require('../../../../utils/lodash.js'),
  require('../stageConstants.js'),
  require('../../pipelineConfigProvider.js'),
  require('./templates/disableAsgExecutionDetails.controller'),
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
