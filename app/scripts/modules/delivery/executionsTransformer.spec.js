'use strict';

describe('executionsService', function() {

  beforeEach(module('deckApp.delivery'));

  beforeEach(inject(function(executionsTransformer) {
    this.transformer = executionsTransformer;
  }));

  describe('transformExecution', function() {
    it('should group stages into summaries when no synthetic stages added', function() {
      var execution = {
        stages: [
          { id: '1', name: 'bake' },
          { id: '2', name: 'deploy' },
          { id: '3', name: 'wait' },
        ]
      };
      this.transformer.transformExecution(execution);

      expect(execution.stageSummaries.length).toBe(3);
      expect(_.pluck(execution.stageSummaries, 'name')).toEqual(['bake', 'deploy', 'wait']);
    });

    it('should group synthetic stages based on parent id and order of entry in execution', function() {
      var execution = {
        stages: [
          { id: '1', name: 'bake', status: 'RUNNING' },
          { id: '2', name: 'deploy' },
          { id: '3', name: 'wait' },
          { id: '4', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE'},
          { id: '5', parentStageId: '1', syntheticStageOwner: 'STAGE_BEFORE'},
          { id: '6', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE'},
          { id: '7', parentStageId: '3', syntheticStageOwner: 'STAGE_BEFORE'},
          { id: '8', parentStageId: '3', syntheticStageOwner: 'STAGE_AFTER'},
        ]
      };
      this.transformer.transformExecution(execution);

      expect(execution.stageSummaries.length).toBe(3);
      expect(_.pluck(execution.stageSummaries[0].stages, 'id')).toEqual(['5','1']);
      expect(_.pluck(execution.stageSummaries[1].stages, 'id')).toEqual(['4','6','2']);
      expect(_.pluck(execution.stageSummaries[2].stages, 'id')).toEqual(['7','3','8']);
    });

    it('should set summary status and start/end times based on child stages', function() {
      var execution = {
        stages: [
          { id: '1', name: 'bake', status: 'COMPLETED', startTime: 7, endTime: 8 },
          { id: '2', name: 'deploy', status: 'RUNNING', startTime: 7 },
          { id: '3', name: 'wait', status: 'NOT_STARTED' },
          { id: '4', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'COMPLETED', startTime: 4, endTime: 6},
          { id: '5', parentStageId: '1', syntheticStageOwner: 'STAGE_BEFORE', status: 'COMPLETED', startTime: 5, endTime: 6},
          { id: '6', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'NOT_STARTED' },
          { id: '7', parentStageId: '3', syntheticStageOwner: 'STAGE_BEFORE', status: 'NOT_STARTED' },
          { id: '8', parentStageId: '3', syntheticStageOwner: 'STAGE_AFTER', status: 'NOT_STARTED' },
        ]
      };
      this.transformer.transformExecution(execution);

      expect(execution.stageSummaries[0].status).toBe('COMPLETED');
      expect(execution.stageSummaries[0].startTime).toBe(5);
      expect(execution.stageSummaries[0].endTime).toBe(8);

      expect(execution.stageSummaries[1].status).toBe('RUNNING');
      expect(execution.stageSummaries[1].startTime).toBe(4);
      expect(execution.stageSummaries[1].endTime).toBeUndefined();

      expect(execution.stageSummaries[2].status).toBe('NOT_STARTED');
      expect(execution.stageSummaries[2].startTime).toBeUndefined();
      expect(execution.stageSummaries[2].endTime).toBeUndefined();
    });
  });
});
