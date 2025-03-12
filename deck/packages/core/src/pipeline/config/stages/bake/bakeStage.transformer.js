'use strict';

import { module } from 'angular';

export const CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE_TRANSFORMER = 'spinnaker.core.pipeline.stage.bake.transformer';
export const name = CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE_TRANSFORMER; // for backwards compatibility
module(CORE_PIPELINE_CONFIG_STAGES_BAKE_BAKESTAGE_TRANSFORMER, []).service('bakeStageTransformer', function () {
  /**
   * Bubbles "previouslyBaked" flag up to parallel bake stage
   */
  function propagatePreviouslyBakedFlag(execution) {
    execution.stages.forEach(function (stage) {
      if (stage.type === 'bake' && stage.context) {
        const childBakeStages = execution.stages.filter(
          (test) => test.type === 'bake' && test.parentStageId === stage.id,
        );
        if (childBakeStages.length) {
          stage.context.allPreviouslyBaked = childBakeStages.every((child) => child.context.previouslyBaked);
          stage.context.somePreviouslyBaked =
            !stage.context.allPreviouslyBaked && childBakeStages.some((child) => child.context.previouslyBaked);
        }
      }
    });
  }

  this.transform = function (application, execution) {
    propagatePreviouslyBakedFlag(execution);
  };
});
