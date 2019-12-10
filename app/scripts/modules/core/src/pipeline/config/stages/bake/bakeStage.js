'use strict';

import { ManualExecutionBake } from './ManualExecutionBake';
import { Registry } from 'core/registry';

const angular = require('angular');

export const CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE = 'spinnaker.core.pipeline.stage.bakeStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE; // for backwards compatibility
angular
  .module(CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE, [require('./bakeStage.transformer').name])
  .config(function() {
    Registry.pipeline.registerStage({
      useBaseProvider: true,
      label: 'Bake',
      description: 'Bakes an image',
      key: 'bake',
      restartable: true,
      manualExecutionComponent: ManualExecutionBake,
    });
  })
  .run([
    'bakeStageTransformer',
    function(bakeStageTransformer) {
      Registry.pipeline.registerTransformer(bakeStageTransformer);
    },
  ]);
