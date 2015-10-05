'use strict';

describe('Service: executionService', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var executionService;

  beforeEach(
    window.module(
      require('./execution.service')
    )
  );

  beforeEach(
    window.inject(function (_executionService_) {
      executionService = _executionService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(executionService).toBeDefined();
  });
});

