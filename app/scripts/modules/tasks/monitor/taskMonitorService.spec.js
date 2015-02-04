'use strict';

describe('Service: taskMonitorService', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var taskMonitorService;

  beforeEach(
    module('deckApp.tasks.monitor.service')
  );

  beforeEach(
    inject(function (_taskMonitorService_) {
      taskMonitorService = _taskMonitorService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(taskMonitorService).toBeDefined();
  });
});

