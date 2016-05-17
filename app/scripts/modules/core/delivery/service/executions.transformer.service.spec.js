'use strict';


describe('executionTransformerService', function() {


  beforeEach(
    window.module(
      require('./executions.transformer.service')
    )
  );

  beforeEach(window.inject(function(executionsTransformer, ___) {
    this.transformer = executionsTransformer;
    this._ = ___;
  }));

  describe('transformExecution', function() {
    it('should flatten stages into summaries', function() {
      var execution = {
        stages: [
          { id: 'a', name: 'a' },
          { id: 'b', name: 'b', parentStageId: 'a', syntheticStageOwner: 'STAGE_BEFORE' },
          { id: 'c', name: 'c', parentStageId: 'a', syntheticStageOwner: 'STAGE_BEFORE' },
          { id: 'd', name: 'd', parentStageId: 'a', syntheticStageOwner: 'STAGE_AFTER' },
          { id: 'e', name: 'e', parentStageId: 'b', syntheticStageOwner: 'STAGE_BEFORE' },
          { id: 'f', name: 'f', parentStageId: 'b', syntheticStageOwner: 'STAGE_BEFORE' },
          { id: 'g', name: 'g', parentStageId: 'd', syntheticStageOwner: 'STAGE_BEFORE' },
          { id: 'h', name: 'h', parentStageId: 'd', syntheticStageOwner: 'STAGE_AFTER' },
        ]
      };

      this.transformer.transformExecution({}, execution);
      expect(_.pluck(execution.stageSummaries[0].stages, 'id')).toEqual(['e', 'f', 'b', 'c', 'a', 'g', 'd', 'h']);
    });

    it('should sort sibling before stages by start time if available', function() {
      var execution = {
        stages: [
          { id: 'a', name: 'a' },
          { id: 'b', name: 'b', parentStageId: 'a', syntheticStageOwner: 'STAGE_BEFORE', startTime: 2 },
          { id: 'c', name: 'c', parentStageId: 'a', syntheticStageOwner: 'STAGE_BEFORE', startTime: 1 },
          { id: 'd', name: 'd', parentStageId: 'a', syntheticStageOwner: 'STAGE_AFTER' },
          { id: 'e', name: 'e', parentStageId: 'b', syntheticStageOwner: 'STAGE_BEFORE' },
          { id: 'f', name: 'f', parentStageId: 'b', syntheticStageOwner: 'STAGE_BEFORE', startTime: 1 },
          { id: 'g', name: 'g', parentStageId: 'd', syntheticStageOwner: 'STAGE_BEFORE' },
          { id: 'h', name: 'h', parentStageId: 'd', syntheticStageOwner: 'STAGE_AFTER' },
        ]
      };

      this.transformer.transformExecution({}, execution);
      expect(_.pluck(execution.stageSummaries[0].stages, 'id')).toEqual(['c', 'f', 'e', 'b', 'a', 'g', 'd', 'h']);
    });

    it('should group stages into summaries when no synthetic stages added', function() {
      var execution = {
        stages: [
          { id: '1', name: 'bake' },
          { id: '2', name: 'deploy' },
          { id: '3', name: 'wait' },
        ]
      };
      this.transformer.transformExecution({}, execution);

      expect(execution.stageSummaries.length).toBe(3);
      expect(_.pluck(execution.stageSummaries, 'name')).toEqual(['bake', 'deploy', 'wait']);
    });

    it('should group synthetic stages based on parent id and order of entry in execution', function() {
      var execution = {
        stages: [
          { id: '1', name: 'bake', status: 'RUNNING' },
          { id: '4', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE'},
          { id: '2', name: 'deploy' },
          { id: '3', name: 'wait' },
          { id: '5', parentStageId: '1', syntheticStageOwner: 'STAGE_BEFORE'},
          { id: '6', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE'},
          { id: '7', parentStageId: '3', syntheticStageOwner: 'STAGE_BEFORE'},
          { id: '8', parentStageId: '3', syntheticStageOwner: 'STAGE_AFTER'},
        ]
      };
      this.transformer.transformExecution({}, execution);

      expect(execution.stageSummaries.length).toBe(3);
      expect(_.pluck(execution.stageSummaries[0].stages, 'id')).toEqual(['5', '1']);
      expect(_.pluck(execution.stageSummaries[1].stages, 'id')).toEqual(['4', '6', '2']);
      expect(_.pluck(execution.stageSummaries[2].stages, 'id')).toEqual(['7', '3', '8']);
    });

    it('should set summary status and start/end times based on child stages', function() {
      var execution = {
        stages: [
          { id: '1', name: 'bake', status: 'SUCCEEDED', startTime: 7, endTime: 8 },
          { id: '2', name: 'deploy', status: 'RUNNING', startTime: 7 },
          { id: '3', name: 'wait', status: 'NOT_STARTED' },
          { id: '4', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'SUCCEEDED', startTime: 4, endTime: 6},
          { id: '5', parentStageId: '1', syntheticStageOwner: 'STAGE_BEFORE', status: 'SUCCEEDED', startTime: 5, endTime: 6},
          { id: '6', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'NOT_STARTED' },
          { id: '7', parentStageId: '3', syntheticStageOwner: 'STAGE_BEFORE', status: 'NOT_STARTED' },
          { id: '8', parentStageId: '3', syntheticStageOwner: 'STAGE_AFTER', status: 'NOT_STARTED' },
        ]
      };
      this.transformer.transformExecution({}, execution);

      expect(execution.stageSummaries[0].status).toBe('SUCCEEDED');
      expect(execution.stageSummaries[0].startTime).toBe(5);
      expect(execution.stageSummaries[0].endTime).toBe(8);

      expect(execution.stageSummaries[1].status).toBe('RUNNING');
      expect(execution.stageSummaries[1].startTime).toBe(4);
      expect(execution.stageSummaries[1].endTime).toBeUndefined();

      expect(execution.stageSummaries[2].status).toBe('NOT_STARTED');
      expect(execution.stageSummaries[2].startTime).toBeUndefined();
      expect(execution.stageSummaries[2].endTime).toBeUndefined();
    });

    it('should set summary status to canceled', function () {
      var execution = {
        stages: [
          { id: '2', name: 'deploy', status: 'SUCCEEDED', startTime: 7, endTime: 8 },
          { id: '4', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'CANCELED', startTime: 4, endTime: 6},
          { id: '6', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'NOT_STARTED', startTime: 6 },
        ]
      };
      this.transformer.transformExecution({}, execution);
      expect(execution.stageSummaries[0].status).toBe('CANCELED');
    });

    it('should set summary status to not started', function () {
      var execution = {
        stages: [
          { id: '2', name: 'deploy', status: 'SUCCEEDED', startTime: 7, endTime: 8 },
          { id: '4', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'COMPLETED', startTime: 4, endTime: 6},
          { id: '6', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'NOT_STARTED', startTime: 6 },
        ]
      };
      this.transformer.transformExecution({}, execution);
      expect(execution.stageSummaries[0].status).toBe('NOT_STARTED');
    });

    it('should remove stage summary end time if current stage is still running', function () {
      var execution = {
        stages: [
          { id: '2', name: 'deploy', status: 'SUCCEEDED', startTime: 7, endTime: 8 },
          { id: '4', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'SUCCEEDED', startTime: 4, endTime: 6},
          { id: '6', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'RUNNING', startTime: 6 },
        ]
      };
      this.transformer.transformExecution({}, execution);
      expect(execution.stageSummaries[0].endTime).toBeUndefined();
    });

    it('should set stage summary end time to the maximum value of all stage end times', function () {
      var execution = {
        stages: [
          { id: '2', name: 'deploy', status: 'SUCCEEDED', startTime: 7, endTime: 8 },
          { id: '4', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'SUCCEEDED', startTime: 4, endTime: 6},
          { id: '5', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'SUCCEEDED', startTime: 4, endTime: 9},
          { id: '6', parentStageId: '2', syntheticStageOwner: 'STAGE_AFTER', status: 'SUCCEEDED', startTime: 6, endTime: 11 },
        ]
      };
      this.transformer.transformExecution({}, execution);
      expect(execution.stageSummaries[0].endTime).toBe(11);
    });

    it('should not set stage status, start/end times based on child stages on non-summary stages', function() {
      var execution = {
        stages: [
          { id: '1', name: 'bake', status: 'SUCCEEDED', startTime: 7, endTime: 8 },
          { id: '2', parentStageId: '1', syntheticStageOwner: 'STAGE_AFTER', status: 'SUCCEEDED', startTime: 8, endTime: 9},
          { id: '3', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'SUCCEEDED', startTime: 7, endTime: 8},
          { id: '4', parentStageId: '2', syntheticStageOwner: 'STAGE_AFTER', status: 'RUNNING', startTime: 11 },
          { id: '5', parentStageId: '2', syntheticStageOwner: 'STAGE_AFTER', status: 'NOT_STARTED' },
        ]
      };
      this.transformer.transformExecution({}, execution);

      var summary = execution.stageSummaries[0];

      expect(execution.stageSummaries.length).toBe(1);

      expect(summary.status).toBe('RUNNING');
      expect(summary.startTime).toBe(7);
      expect(summary.endTime).toBeUndefined();

      expect(summary.stages.length).toBe(5);

      var nested = summary.stages[2];

      expect(_.pluck(summary.stages, 'id')).toEqual(['1', '3', '2', '4', '5']);
      expect(nested.id).toBe('2');
      expect(nested.status).toBe('SUCCEEDED');
      expect(nested.startTime).toBe(8);
      expect(nested.endTime).toBe(9);
    });

    it('should add startTime to stage summary based on min of child stage start times if first stage has no start time', function () {
      var execution = {
        stages: [
          { id: '2', name: 'deploy', status: 'SUCCEEDED', startTime: null, endTime: 8 },
          { id: '4', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'SUCCEEDED', startTime: null, endTime: 6},
          { id: '5', parentStageId: '2', syntheticStageOwner: 'STAGE_BEFORE', status: 'SUCCEEDED', startTime: 11, endTime: 9},
          { id: '6', parentStageId: '2', syntheticStageOwner: 'STAGE_AFTER', status: 'SUCCEEDED', startTime: 4, endTime: 11 },
        ]
      };
      this.transformer.transformExecution({}, execution);
      expect(execution.stageSummaries[0].startTime).toBe(4);
    });
  });

  describe('buildInfo', function () {
    let deployStage, lastBuild, parentBuild, triggerBuild;

    beforeEach(function () {
      lastBuild = { number: 4 };
      parentBuild = { number: 5 };
      triggerBuild = { number: 6 };
      deployStage = { type: 'deploy', context: {
        deploymentDetails: [
          { jenkins: { number: 3, host: 'http://jenkinshost/', name: 'jobName' } }
        ]
      }};
    });

    it('adds buildInfo from deployment details', function () {
      let execution = { stages: [ deployStage ] };
      this.transformer.transformExecution({}, execution);
      expect(execution.buildInfo).toEqual({ number: 3, url: 'http://jenkinshost/job/jobName/3'});
    });

    it('adds buildInfo from lastBuild if present', function () {
      let execution = { stages: [], trigger: { buildInfo: {lastBuild: lastBuild }}};
      this.transformer.transformExecution({}, execution);
      expect(execution.buildInfo.number).toBe(4);
    });

    it('adds buildInfo from trigger', function () {
      let execution = { stages: [], trigger: { buildInfo: triggerBuild }};
      this.transformer.transformExecution({}, execution);
      expect(execution.buildInfo.number).toBe(6);
    });

    it('adds buildInfo from parent pipeline', function () {
      let execution = { stages: [], trigger: { parentExecution: {trigger: {buildInfo: parentBuild }}}};
      this.transformer.transformExecution({}, execution);
      expect(execution.buildInfo.number).toBe(5);
    });

    it('prefers deployment buildInfo to all others', function () {
      let execution = { stages: [ deployStage ], trigger: { buildInfo: triggerBuild } };
      execution.trigger.buildInfo.lastBuild = lastBuild;
      execution.trigger.parentExecution = { trigger: { buildInfo: parentBuild } };
      this.transformer.transformExecution({}, execution);
      expect(execution.buildInfo).toEqual({ number: 3, url: 'http://jenkinshost/job/jobName/3'});
    });

    it('prefers last build info to parent execution or trigger details', function () {
      let execution = { stages: [ ], trigger: { buildInfo: triggerBuild } };
      execution.trigger.buildInfo.lastBuild = lastBuild;
      execution.trigger.parentExecution = { trigger: { buildInfo: parentBuild } };
      this.transformer.transformExecution({}, execution);
      expect(execution.buildInfo.number).toBe(4);
    });

    it('prefers trigger details to parent execution', function () {
      let execution = { stages: [ ], trigger: { buildInfo: triggerBuild } };
      execution.trigger.parentExecution = { trigger: { buildInfo: parentBuild } };
      this.transformer.transformExecution({}, execution);
      expect(execution.buildInfo.number).toBe(6);
    });
  });
});
