'use strict';

describe('Service: securityGroupService', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var securityGroupService;

  beforeEach(
    module('deckApp.securityGroup.service')
  );

  beforeEach(
    inject(function (_securityGroupService_) {
      securityGroupService = _securityGroupService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(securityGroupService).toBeDefined();
  });
});


