'use strict';

import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.bakeStage', [
    PIPELINE_CONFIG_PROVIDER,
    require('./bakeStage.transformer.js'),
  ])
  .config(function(pipelineConfigProvider) {
    pipelineConfigProvider.registerStage({
      useBaseProvider: true,
      label: 'Bake',
      description: 'Bakes an image in the specified region',
      key: 'bake',
      restartable: true,
    });
  })
  .run(function(pipelineConfig, bakeStageTransformer) {
    pipelineConfig.registerTransformer(bakeStageTransformer);
  });
