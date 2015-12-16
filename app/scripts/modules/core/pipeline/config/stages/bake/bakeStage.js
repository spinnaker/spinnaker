'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.bakeStage', [
    require('../../pipelineConfigProvider.js'),
    require('./bakeStage.transformer.js'),
  ])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      label: 'Bake',
      description: 'Bakes an image in the specified region',
      key: 'bake',
    });
  })
  .run(function(pipelineConfig, bakeStageTransformer) {
    pipelineConfig.registerTransformer(bakeStageTransformer);
  });
