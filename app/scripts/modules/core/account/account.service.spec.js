'use strict';

describe('Service: accountService ', function () {

  var accountService, $http, settings, cloudProviderRegistry, API;

  beforeEach(
    window.module(
      require('./account.service'),
      require('../../core/api/api.service')
    )
  );

  beforeEach(
    window.inject(function (_accountService_, $httpBackend, _settings_, _cloudProviderRegistry_, _API_) {
      API = _API_;
      accountService = _accountService_;
      $http = $httpBackend;
      settings = _settings_;
      cloudProviderRegistry = _cloudProviderRegistry_;

    })
  );

  it('should filter the list of accounts by provider when supplied', function () {
    $http.expectGET(API.baseUrl + '/credentials').respond(200, [
      {name: 'test', type: 'aws'},
      {name: 'prod', type: 'aws'},
      {name: 'prod', type: 'gce'},
      {name: 'gce-test', type: 'gce'},
    ]);

    var accounts = null;
    accountService.listAccounts('aws').then(function (results) {
      accounts = results;
    });

    $http.flush();

    expect(accounts.length).toBe(2);
    expect(_.pluck(accounts, 'name')).toEqual(['test', 'prod']);
  });

  describe('getAllAccountDetailsForProvider', function () {

    it('should return details for each account', function () {
      $http.expectGET(API.baseUrl + '/credentials').respond(200, [
        {name: 'test', type: 'aws'},
        {name: 'prod', type: 'aws'},
      ]);

      $http.expectGET(API.baseUrl + '/credentials/test').respond(200, { a: 1});
      $http.expectGET(API.baseUrl + '/credentials/prod').respond(200, { a: 2});

      var details = null;
      accountService.getAllAccountDetailsForProvider('aws').then((results) => {
        details = results;
      });

      $http.flush();

      expect(details.length).toBe(2);
      expect(details[0].a).toBe(1);
      expect(details[1].a).toBe(2);

    });

    it('should fall back to an empty array if an exception occurs when listing accounts', function () {
      $http.expectGET(API.baseUrl + '/credentials').respond(429, null);

      var details = null;
      accountService.getAllAccountDetailsForProvider('aws').then((results) => {
        details = results;
      });

      $http.flush();

      expect(details).toEqual([]);
    });

    it('should fall back to an empty array if an exception occurs when getting details for an account', function () {
      $http.expectGET(API.baseUrl + '/credentials').respond(200, [
        {name: 'test', type: 'aws'},
        {name: 'prod', type: 'aws'},
      ]);

      $http.expectGET(API.baseUrl + '/credentials/test').respond(500, null);
      $http.expectGET(API.baseUrl + '/credentials/prod').respond(200, { a: 2});

      var details = null;
      accountService.getAllAccountDetailsForProvider('aws').then((results) => {
        details = results;
      });

      $http.flush();

      expect(details).toEqual([]);
    });

  });

  describe('listProviders', function () {

    beforeEach(function() {
      this.registeredProviders = ['aws', 'gce', 'cf'];
      $http.whenGET(API.baseUrl + '/credentials').respond(200,
        [ { type: 'aws' }, { type: 'gce' }, { type: 'cf' }]
      );

      spyOn(cloudProviderRegistry, 'listRegisteredProviders').and.returnValue(this.registeredProviders);
    });

    it('should list all providers when no application provided', function () {

      let test = (result) => expect(result).toEqual(['aws', 'cf', 'gce']);

      accountService.listProviders().then(test);

      $http.flush();
    });

    it('should filter out providers not registered', function () {
      this.registeredProviders.pop();

      let test = (result) => expect(result).toEqual(['aws', 'gce']);

      accountService.listProviders().then(test);

      $http.flush();
    });

    it('should fall back to the defaultProviders if none configured for the application', function () {
      let application = { attributes: {} };

      let test = (result) => expect(result).toEqual(['cf', 'gce']);

      settings.defaultProviders = ['gce', 'cf'];

      accountService.listProviders(application).then(test);

      $http.flush();
    });

    it('should return the intersection of those configured for the application and those available from the server', function () {
      let application = { attributes: { cloudProviders: 'gce,cf,unicron' } };

      let test = (result) => expect(result).toEqual(['cf', 'gce']);

      settings.defaultProviders = ['aws'];

      accountService.listProviders(application).then(test);

      $http.flush();
    });

    it('should return an empty array if none of the app providers are available from the server', function () {
      let application = { attributes: { cloudProviders: 'lamp,ceiling fan' } };

      let test = (result) => expect(result).toEqual([]);

      settings.defaultProviders = 'aws';

      accountService.listProviders(application).then(test);

      $http.flush();
    });

    it('should fall back to all registered available providers if no defaults configured and none configured on app', function () {
      let application = { attributes: {} };

      let test = (result) => expect(result).toEqual(['aws', 'cf', 'gce']);

      delete settings.defaultProviders;

      accountService.listProviders(application).then(test);

      $http.flush();
    });

  });
});


