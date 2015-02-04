'use strict';

describe('Service: accountService ', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var accountService;

  beforeEach(
    module('deckApp.account.service')
  );

  beforeEach(
    inject(function (_accountService_) {
      accountService = _accountService_;
    })
  );

  it('should instantiate the controller', function () {
    expect(accountService).toBeDefined();
  });
});


