'use strict';

import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';

describe('Pipeline Trigger: ExecutionHandler', function() {
  var $scope, handler, $q;

  beforeEach(window.module(require('./pipelineTrigger.module.js').name));

  beforeEach(
    window.inject(function($rootScope, pipelineTriggerManualExecutionHandler, _$q_) {
      $scope = $rootScope.$new();
      handler = pipelineTriggerManualExecutionHandler;
      $q = _$q_;
    }),
  );

  it('gets pipeline name from configs', function() {
    let label = null;
    spyOn(PipelineConfigService, 'getPipelinesForApplication').and.returnValue(
      $q.when([{ id: 'b', name: 'expected' }, { id: 'a', name: 'other' }]),
    );

    handler.formatLabel({ application: 'a', pipeline: 'b' }).then(result => (label = result));
    $scope.$digest();
    expect(label).toBe('(Pipeline) a: expected');
  });

  it('returns error message if pipeline config is not found', function() {
    let label = null;
    spyOn(PipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([{ id: 'a', name: 'other' }]));

    handler.formatLabel({ application: 'a', pipeline: 'b' }).then(result => (label = result));
    $scope.$digest();
    expect(label).toBe('[pipeline not found]');
  });

  it('returns error message if pipelines cannot be loaded', function() {
    let label = null;
    spyOn(PipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.reject(''));

    handler.formatLabel({ application: 'a', pipeline: 'b' }).then(result => (label = result));
    $scope.$digest();
    expect(label).toBe(`[could not load pipelines for 'a']`);
  });
});
