'use strict';

describe('Service: executionsService', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var executionsService;

  beforeEach(
    module('deckApp.delivery.executions.service')
  );

  beforeEach(
    inject(function (_executionsService_) {
      executionsService = _executionsService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(executionsService).toBeDefined();
  });
});

