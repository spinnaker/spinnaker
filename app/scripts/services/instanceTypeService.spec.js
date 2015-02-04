'use strict';

describe('Service: instanceTypeService', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var instanceTypeService;

  beforeEach(
    module('deckApp.instanceType.service')
  );

  beforeEach(
    inject(function (_instanceTypeService_) {
      instanceTypeService = _instanceTypeService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(instanceTypeService).toBeDefined();
  });
});


