'use strict';

describe('bakeStageTransformer', function () {
  beforeEach(window.module(require('./bakeStage.transformer').name));

  beforeEach(
    window.inject(function (bakeStageTransformer) {
      this.transformer = bakeStageTransformer;
    }),
  );

  describe('transform', function () {
    it('should add allPreviouslyBaked/somePreviouslyBaked flags when all/some child bake stages are marked previouslyBaked', function () {
      var execution = {
        stages: [
          { id: 'a', name: 'a', context: {} },
          { id: 'b', name: 'b', parentStageId: 'a', context: { previouslyBaked: true } },
          { id: 'c', name: 'c', parentStageId: 'a', context: { previouslyBaked: true } },
          { id: 'e', name: 'e', context: {} },
          { id: 'f', name: 'f', parentStageId: 'e', context: { previouslyBaked: true } },
          { id: 'g', name: 'g', parentStageId: 'e', context: { previouslyBaked: false } },
          { id: 'h', name: 'h', context: {} },
          { id: 'i', name: 'i', parentStageId: 'h', context: { previouslyBaked: false } },
          { id: 'j', name: 'j', context: {} },
        ],
      };

      execution.stages.forEach((stage) => (stage.type = 'bake'));

      this.transformer.transform({}, execution);

      expect(execution.stages[0].context.allPreviouslyBaked).toBe(true);
      expect(execution.stages[0].context.somePreviouslyBaked).toBe(false);

      expect(execution.stages[3].context.allPreviouslyBaked).toBe(false);
      expect(execution.stages[3].context.somePreviouslyBaked).toBe(true);

      expect(execution.stages[6].context.allPreviouslyBaked).toBe(false);
      expect(execution.stages[6].context.somePreviouslyBaked).toBe(false);

      expect(execution.stages[8].context.allPreviouslyBaked).toBe(undefined);
      expect(execution.stages[8].context.somePreviouslyBaked).toBe(undefined);
    });
  });
});
