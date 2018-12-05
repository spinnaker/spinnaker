'use strict';

import { API } from '@spinnaker/core';

import { AwsImageReader } from './image.reader';

describe('Service: aws Image Reader', function() {
  var service, $http, scope;

  beforeEach(
    window.inject(function($httpBackend, $rootScope) {
      service = new AwsImageReader();
      $http = $httpBackend;
      scope = $rootScope.$new();
    }),
  );

  afterEach(function() {
    $http.verifyNoOutstandingRequest();
    $http.verifyNoOutstandingExpectation();
  });

  describe('findImages', function() {
    var query = 'abc',
      region = 'us-west-1';

    function buildQueryString() {
      return API.baseUrl + '/images/find?provider=aws&q=' + query + '&region=' + region;
    }

    it('queries gate when 3 characters are supplied', function() {
      var result = null;

      $http.when('GET', buildQueryString()).respond(200, [{ success: true }]);

      service.findImages({ provider: 'aws', q: query, region: region }).then(function(results) {
        result = results;
      });

      $http.flush();

      expect(result.length).toBe(1);
      expect(result[0].success).toBe(true);
    });

    it('queries gate when more than 3 characters are supplied', function() {
      var result = null;

      query = 'abcd';

      $http.when('GET', buildQueryString()).respond(200, [{ success: true }]);

      var promise = service.findImages({ provider: 'aws', q: query, region: region });

      promise.then(function(results) {
        result = results;
      });

      $http.flush();

      expect(result.length).toBe(1);
      expect(result[0].success).toBe(true);
    });

    it('returns a message prompting user to enter more characters when less than 3 are supplied', function() {
      query = 'ab';

      var result = null;

      service.findImages({ provider: 'aws', q: query, region: region }).then(function(results) {
        result = results;
      });

      scope.$digest();

      expect(result.length).toBe(1);
      expect(result[0].message).toBe('Please enter at least 3 characters...');
    });

    it('returns an empty array when server errors', function() {
      query = 'abc';
      var result = null;

      $http.when('GET', buildQueryString()).respond(404, {});

      service.findImages({ provider: 'aws', q: query, region: region }).then(function(results) {
        result = results;
      });

      $http.flush();

      expect(result.length).toBe(0);
    });
  });

  describe('getImage', function() {
    var imageName = 'abc',
      region = 'us-west-1',
      credentials = 'test';

    function buildQueryString() {
      return [API.baseUrl, 'images', credentials, region, imageName].join('/') + '?provider=aws';
    }

    it('returns null if server returns 404 or an empty list', function() {
      var result = 'not null';

      $http.when('GET', buildQueryString()).respond(404, {});

      service.getImage(imageName, region, credentials).then(function(results) {
        result = results;
      });

      $http.flush();

      expect(result).toBe(null);

      result = 'not null';

      $http.when('GET', buildQueryString()).respond(200, []);

      service.getImage(imageName, region, credentials).then(function(results) {
        result = results;
      });

      $http.flush();

      expect(result).toBe(null);
    });
  });
});
