'use strict';

describe('Service: executionsService', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var executionsService;

  beforeEach(
    window.module(
      require('./executionsService')
    )
  );

  beforeEach(
    window.inject(function (_executionsService_) {
      executionsService = _executionsService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(executionsService).toBeDefined();
  });
});

