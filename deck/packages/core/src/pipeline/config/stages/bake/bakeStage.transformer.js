/**
 * Bubbles "previouslyBaked" flag up to parallel bake stage.
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

export const bakeStageTransformer = {
  transform(application, execution) {
    propagatePreviouslyBakedFlag(execution);
  },
};
