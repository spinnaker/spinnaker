'use strict';

import { API_SERVICE } from '@spinnaker/core';

describe('Service: DCOS Image Reader', function() {

  var service, $http, API;

  beforeEach(
    window.module(
      require('./image.reader.js'),
      API_SERVICE
    )
  );


  beforeEach(window.inject(function (dcosImageReader, $httpBackend, _API_) {
    API = _API_;
    service = dcosImageReader;
    $http = $httpBackend;
  }));

  afterEach(function() {
    $http.verifyNoOutstandingRequest();
    $http.verifyNoOutstandingExpectation();
  });

  describe('findImages', function () {

    var query = 'abc', region = 'usw';

    function buildQueryString() {
      return API.baseUrl + '/images/find?provider=dcos&q=' + query + '&region=' + region;
    }

    it('queries gate when 3 characters are supplied', function() {
      var result = null;

      $http.when('GET', buildQueryString()).respond(200, [
        {success: true}
      ]);

      service.findImages({provider: 'dcos', q: query, region: region}).then(function (results) {
        result = results;
      });

      $http.flush();

      expect(result.length).toBe(1);
      expect(result[0].success).toBe(true);
    });


    it('queries gate when more than 3 characters are supplied', function() {
      var result = null;

      query = 'abcd';

      $http.when('GET', buildQueryString()).respond(200, [
        {success: true}
      ]);

      var promise = service.findImages({provider: 'dcos', q: query, region: region});

      promise.then(function (results) {
        result = results;
      });

      $http.flush();

      expect(result.length).toBe(1);
      expect(result[0].success).toBe(true);
    });

    it('returns an empty array when server errors', function() {
      query = 'abc';
      var result = null;

      $http.when('GET', buildQueryString()).respond(404, {});

      service.findImages({provider: 'dcos', q: query, region: region}).then(function(results) {
        result = results;
      });

      $http.flush();

      expect(result.length).toBe(0);
    });
  });
});
