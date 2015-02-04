'use strict';

describe('Service: gceInstanceTypeService', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var gceInstanceTypeService;

  beforeEach(
    module('deckApp.gce.instanceType.service')
  );

  beforeEach(
    inject(function (_gceInstanceTypeService_) {
      gceInstanceTypeService = _gceInstanceTypeService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(gceInstanceTypeService).toBeDefined();
  });
});


