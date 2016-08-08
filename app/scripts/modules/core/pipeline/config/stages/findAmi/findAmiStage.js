'use strict';

let angular = require('angular');

//BEN_TODO
module.exports = angular.module('spinnaker.core.pipeline.stage.findAmiStage', [
  require('../../pipelineConfigProvider.js')
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      key: 'findImage',
      label: 'Find Image from Cluster',
      description: 'Finds an image to deploy from an existing cluster'
    });
  });

