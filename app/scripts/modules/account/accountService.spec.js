'use strict';

describe('Service: accountService ', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var accountService, $http, settings;

  beforeEach(
    module('deckApp.account.service')
  );

  beforeEach(
    inject(function (_accountService_, $httpBackend) {
      accountService = _accountService_;
      $http = $httpBackend;
    })
  );

  it('should filter the list of accounts by provider when supplied', function () {
    $http.expectGET('/credentials').respond(200, [
      { name: 'test', type: 'aws' },
      { name: 'prod', type: 'aws' },
      { name: 'prod', type: 'gce' },
      { name: 'gce-test', type: 'gce' },
    ]);

    var accounts = null;
    accountService.listAccounts('aws').then(function(results) {
      accounts = results;
    });

    $http.flush();

    expect(accounts.length).toBe(2);
    expect(_.pluck(accounts, 'name')).toEqual(['test', 'prod']);

  });
});


