'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pipeline.stage.bake.transformer', [
  ])
  .service('bakeStageTransformer', function() {

    /**
     * Bubbles "previouslyBaked" flag up to parallel bake stage
     */
    function propagatePreviouslyBakedFlag(execution) {
      execution.stages.forEach(function(stage) {
        if (stage.type === 'bake' && stage.context) {
          let childBakeStages = execution.stages.filter((test) => test.type === 'bake' && test.parentStageId === stage.id);
          if (childBakeStages.length) {
            stage.context.allPreviouslyBaked = childBakeStages.every((child) => child.context.previouslyBaked);
            stage.context.somePreviouslyBaked = !stage.context.allPreviouslyBaked &&
              childBakeStages.some((child) => child.context.previouslyBaked);
          }
        }
      });
    }

    this.transform = function(application, execution) {
      propagatePreviouslyBakedFlag(execution);
    };
  }).name;
