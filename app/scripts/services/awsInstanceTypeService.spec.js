'use strict';

describe('Service: awsInstanceTypeService', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var awsInstanceTypeService;

  beforeEach(
    module('deckApp.aws.instanceType.service')
  );

  beforeEach(
    inject(function (_awsInstanceTypeService_) {
      awsInstanceTypeService = _awsInstanceTypeService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(awsInstanceTypeService).toBeDefined();
  });
});

