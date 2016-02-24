'use strict';

describe('Pipeline Trigger: ExecutionHandler', function() {

  var $scope, handler, pipelineConfigService, $q;

  beforeEach(
    window.module(
      require('./pipelineTrigger.module.js')
    )
  );

  beforeEach(window.inject(function($rootScope, pipelineTriggerManualExecutionHandler, _pipelineConfigService_, _$q_) {
    $scope = $rootScope.$new();
    handler = pipelineTriggerManualExecutionHandler;
    pipelineConfigService = _pipelineConfigService_;
    $q = _$q_;
  }));

  it('gets pipeline name from configs', function () {
    let label = null;
    spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([
      { id: 'b', name: 'expected' },
      { id: 'a', name: 'other' }
    ]));

    handler.formatLabel({application: 'a', pipeline: 'b'}).then((result) => label = result);
    $scope.$digest();
    expect(label).toBe('(Pipeline) a: expected');
  });

  it('returns error message if pipeline config is not found', function () {
    let label = null;
    spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.when([
      { id: 'a', name: 'other' }
    ]));

    handler.formatLabel({application: 'a', pipeline: 'b'}).then((result) => label = result);
    $scope.$digest();
    expect(label).toBe('[pipeline not found]');
  });

  it('returns error message if pipelines cannot be loaded', function () {
    let label = null;
    spyOn(pipelineConfigService, 'getPipelinesForApplication').and.returnValue($q.reject(''));

    handler.formatLabel({application: 'a', pipeline: 'b'}).then((result) => label = result);
    $scope.$digest();
    expect(label).toBe(`[could not load pipelines for 'a']`);
  });

});
