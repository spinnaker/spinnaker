'use strict';

describe('Service: executionFilterService', function () {


  var service;
  var ExecutionFilterModel;
  var $timeout;

  beforeEach(
    window.module(
      require('./executionFilter.service.js'),
      require('./executionFilter.model.js')
    )
  );

  beforeEach(
    window.inject(
      function (_$location_, executionFilterService, _ExecutionFilterModel_, _$timeout_) {
        service = executionFilterService;
        ExecutionFilterModel = _ExecutionFilterModel_;
        $timeout = _$timeout_;
        ExecutionFilterModel.groups = [];
      }
    )
  );

  describe('Updating execution groups', function () {

    it('limits executions per pipeline', function () {
      let application = {
        executions: [
          { pipelineConfigId: '1', name: 'pipeline 1', endTime: 1, stages: [] },
          { pipelineConfigId: '1', name: 'pipeline 1', endTime: 2, stages: [] },
          { pipelineConfigId: '1', name: 'pipeline 1', endTime: 3, stages: [] },
          { pipelineConfigId: '2', name: 'pipeline 2', endTime: 1, stages: [] },
        ],
        pipelineConfigs: [
          { name: 'pipeline 1', pipelineConfigId: '1' },
          { name: 'pipeline 2', pipelineConfigId: '2' },
        ]
      };

      ExecutionFilterModel.sortFilter.count = 2;
      ExecutionFilterModel.sortFilter.groupBy = 'none';

      service.updateExecutionGroups(application);
      $timeout.flush();

      expect(ExecutionFilterModel.groups.length).toBe(1);
      expect(ExecutionFilterModel.groups[0].executions.length).toBe(3);
      expect(ExecutionFilterModel.groups[0].executions.filter((ex) => ex.pipelineConfigId === '1').length).toBe(2);
      expect(ExecutionFilterModel.groups[0].executions.filter((ex) => ex.pipelineConfigId === '2').length).toBe(1);

      ExecutionFilterModel.sortFilter.groupBy = 'name';
      service.updateExecutionGroups(application);
      $timeout.flush();

      expect(ExecutionFilterModel.groups.length).toBe(2);
      expect(ExecutionFilterModel.groups[0].executions.length).toBe(2);
      expect(ExecutionFilterModel.groups[1].executions.length).toBe(1);

    });

  });
});
