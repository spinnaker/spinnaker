'use strict';

import { module } from 'angular';

import { ManualExecutionBake } from './ManualExecutionBake';
import { CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE_TRANSFORMER } from './bakeStage.transformer';
import { Registry } from '../../../../registry';

export const CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE = 'spinnaker.core.pipeline.stage.bakeStage';
export const name = CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE, [CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE_TRANSFORMER])
  .config(function () {
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
    function (bakeStageTransformer) {
      Registry.pipeline.registerTransformer(bakeStageTransformer);
    },
  ]);
