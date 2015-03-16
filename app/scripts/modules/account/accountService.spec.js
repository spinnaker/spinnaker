'use strict';

describe('Service: accountService ', function () {

  //NOTE: This is only testing the service dependencies. Please add more tests.

  var $rootScope, accountService, $http, $q, settings;

  beforeEach(
    module('deckApp.account.service')
  );

  beforeEach(
    inject(function (_$rootScope_, _accountService_, $httpBackend, infrastructureCaches, _$q_, _settings_) {
      $rootScope = _$rootScope_;
      accountService = _accountService_;
      $http = $httpBackend;
      $q = _$q_;
      settings = _settings_;

      if (infrastructureCaches.credentials) {
        infrastructureCaches.credentials.removeAll();
      }
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

  describe('get Availability Zones For Account And Region', function () {


    it('should return intersection of preferred and actual AZ when: actual count > preferred count', function () {

      var accountName = 'prod';
      var regionName = 'us-east-1' ;

      settings.preferredZonesByAccount = {
        prod: {
          'us-east-1': ['us-east-1c', 'us-east-1d', 'us-east-1e'],
        }
      };

      $http.whenGET('/credentials/' + accountName).respond(200,
        {
          regions:[
            {
              name: regionName,
              availabilityZones: [
                'us-east-1a',
                'us-east-1b',
                'us-east-1c',
                'us-east-1d',
                'us-east-1e',
              ]
            },
          ]
        }
      );

      var test = function(result) {
        expect(result).toEqual(['us-east-1c', 'us-east-1d', 'us-east-1e']);
      };

      accountService.getAvailabilityZonesForAccountAndRegion(accountName, regionName).then(test);

      $http.flush();
    });


    it('should return intersection of preferred and actual AZ when: actual count < preferred count', function () {

      var accountName = 'prod';
      var regionName = 'us-east-1' ;

      settings.preferredZonesByAccount = {
        prod: {
          'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1c'],
        }
      };

      $http.whenGET('/credentials/' + accountName).respond(200,
        {
          regions:[
            {
              name: regionName,
              availabilityZones: [
                'us-east-1a',
              ]
            },
          ]
        }
      );

      var test = function(result) {
        expect(result).toEqual(['us-east-1a']);
      };

      accountService.getAvailabilityZonesForAccountAndRegion(accountName, regionName).then(test);

      $http.flush();
    });

    it('should return intersection of preferred and actual AZ when: actual count === preferred count', function () {

      var accountName = 'prod';
      var regionName = 'us-east-1' ;

      settings.preferredZonesByAccount = {
        prod: {
          'us-east-1': ['us-east-1a', 'us-east-1b', 'us-east-1c'],
        }
      };

      $http.whenGET('/credentials/' + accountName).respond(200,
        {
          regions:[
            {
              name: regionName,
              availabilityZones: ['us-east-1a', 'us-east-1b', 'us-east-1c']
            },
          ]
        }
      );

      var test = function(result) {
        expect(result).toEqual(['us-east-1a', 'us-east-1b', 'us-east-1c']);
      };

      accountService.getAvailabilityZonesForAccountAndRegion(accountName, regionName).then(test);

      $http.flush();
    });

    it('should return an empty list when there is no intersection', function () {

      var accountName = 'prod';
      var regionName = 'us-east-1' ;

      settings.preferredZonesByAccount = {
        prod: {
          'us-east-1': ['us-east-1a'],
        }
      };

      $http.whenGET('/credentials/' + accountName).respond(200,
        {
          regions:[
            {
              name: regionName,
              availabilityZones: ['us-east-1d', 'us-east-1e']
            },
          ]
        }
      );

      var test = function(result) {
        expect(result).toEqual([]);
      };

      accountService.getAvailabilityZonesForAccountAndRegion(accountName, regionName).then(test);

      $http.flush();
    });


    it('should return the default AZ if the credential fetch fails for an account', function () {

      var accountName = 'prod';
      var regionName = 'us-east-1' ;

      settings.preferredZonesByAccount = {
        prod: {
          'us-east-1': ['us-east-1b'],
        },
        default: {
          'us-east-1': ['us-east-1a'],
        }
      };

      $http.whenGET('/credentials/' + accountName).respond(500);

      var test = function(result) {
        expect(result).toEqual(settings.preferredZonesByAccount.default[regionName]);
      };

      accountService.getAvailabilityZonesForAccountAndRegion(accountName, regionName).then(test);

      $http.flush();
    });

  });
});


