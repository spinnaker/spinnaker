'use strict';

import { API } from '@spinnaker/core';

describe('Service: DCOS Image Reader', function () {
  var service, $httpBackend;

  beforeEach(window.module(require('./image.reader').name));

  beforeEach(
    window.inject(function (dcosImageReader, _$httpBackend_) {
      service = dcosImageReader;
      $httpBackend = _$httpBackend_;
    }),
  );

  afterEach(function () {
    $httpBackend.verifyNoOutstandingRequest();
    $httpBackend.verifyNoOutstandingExpectation();
  });

  describe('findImages', function () {
    var query = 'abc',
      region = 'usw';

    function buildQueryString() {
      return API.baseUrl + '/images/find?provider=dcos&q=' + query + '&region=' + region;
    }

    it('queries gate when 3 characters are supplied', function () {
      var result = null;

      $httpBackend.when('GET', buildQueryString()).respond(200, [{ success: true }]);

      service.findImages({ provider: 'dcos', q: query, region: region }).then(function (results) {
        result = results;
      });

      $httpBackend.flush();

      expect(result.length).toBe(1);
      expect(result[0].success).toBe(true);
    });

    it('queries gate when more than 3 characters are supplied', function () {
      var result = null;

      query = 'abcd';

      $httpBackend.when('GET', buildQueryString()).respond(200, [{ success: true }]);

      var promise = service.findImages({ provider: 'dcos', q: query, region: region });

      promise.then(function (results) {
        result = results;
      });

      $httpBackend.flush();

      expect(result.length).toBe(1);
      expect(result[0].success).toBe(true);
    });

    it('returns an empty array when server errors', function () {
      query = 'abc';
      var result = null;

      $httpBackend.when('GET', buildQueryString()).respond(404, {});

      service.findImages({ provider: 'dcos', q: query, region: region }).then(function (results) {
        result = results;
      });

      $httpBackend.flush();

      expect(result.length).toBe(0);
    });
  });
});
