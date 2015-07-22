'use strict';

describe('Service: gceInstanceTypeService', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var gceInstanceTypeService;

  beforeEach(
    window.module(require('./gceInstanceTypeService'))
  );

  beforeEach(
    window.inject(function (_gceInstanceTypeService_) {
      gceInstanceTypeService = _gceInstanceTypeService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(gceInstanceTypeService).toBeDefined();
  });
});


