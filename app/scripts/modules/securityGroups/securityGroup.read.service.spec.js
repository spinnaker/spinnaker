'use strict';

describe('Service: securityGroupReader', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var securityGroupReader;

  beforeEach(
    module('deckApp.securityGroup.read.service')
  );

  beforeEach(
    inject(function (_securityGroupReader_) {
      securityGroupReader = _securityGroupReader_;
    })
  );

  it('should instantiate the controller', function () {
    expect(securityGroupReader).toBeDefined();
  });
});


