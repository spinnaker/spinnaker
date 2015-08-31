'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stage.bakeStage', [
  require('../../pipelineConfigProvider.js'),
])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      label: 'Bake',
      description: 'Bakes an image in the specified region',
      key: 'bake',
    });
  }).name;
