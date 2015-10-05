'use strict';

describe('Service: cfInstanceTypeService', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var cfInstanceTypeService;

  beforeEach(
    window.module(require('./cfInstanceTypeService'))
  );

  beforeEach(
    window.inject(function (_cfInstanceTypeService_) {
      cfInstanceTypeService = _cfInstanceTypeService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(cfInstanceTypeService).toBeDefined();
  });
});


