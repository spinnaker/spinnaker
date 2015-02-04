'use strict';

describe('Service: executionTransformer', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var executionsTransformer;

  beforeEach(
    module('deckApp.delivery.executionTransformer.service')
  );

  beforeEach(
    inject(function (_executionsTransformer_) {
      executionsTransformer = _executionsTransformer_;
    })
  );

  it('should instantiate the controller', function () {
    expect(executionsTransformer).toBeDefined();
  });
});

