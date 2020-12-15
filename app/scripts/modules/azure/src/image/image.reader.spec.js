'use strict';
import { mockHttpClient } from 'core/api/mock/jasmine';

describe('Service: Azure Image Reader', function () {
  var service;

  beforeEach(window.module(require('./image.reader').name));

  beforeEach(
    window.inject(function (azureImageReader) {
      service = azureImageReader;
    }),
  );

  describe('findImages', function () {
    var query = 'abc',
      region = 'usw';

    const buildQueryString = () => `/images/find?provider=azure&q=${query}&region=${region}`;

    it('queries gate when 3 characters are supplied', async function () {
      const http = mockHttpClient();
      var result = null;

      http.expectGET(buildQueryString()).respond(200, [{ success: true }]);

      service.findImages({ provider: 'azure', q: query, region: region }).then(function (results) {
        result = results;
      });

      await http.flush();

      expect(result.length).toBe(1);
      expect(result[0].success).toBe(true);
    });

    it('queries gate when more than 3 characters are supplied', async function () {
      const http = mockHttpClient();
      var result = null;

      query = 'abcd';

      http.expectGET(buildQueryString()).respond(200, [{ success: true }]);

      var promise = service.findImages({ provider: 'azure', q: query, region: region });

      promise.then(function (results) {
        result = results;
      });

      await http.flush();

      expect(result.length).toBe(1);
      expect(result[0].success).toBe(true);
    });

    it('returns an empty array when server errors', async function () {
      const http = mockHttpClient();
      query = 'abc';
      var result = null;

      http.expectGET(buildQueryString()).respond(404, {});

      service.findImages({ provider: 'azure', q: query, region: region }).then(function (results) {
        result = results;
      });

      await http.flush();

      expect(result.length).toBe(0);
    });
  });

  describe('getImage', function () {
    var imageName = 'abc',
      region = 'usw',
      credentials = 'test';

    const buildQueryString = () => `/images/${credentials}/${region}/${imageName}?provider=azure`;

    it('returns null if server returns 404 or an empty list', async function () {
      const http = mockHttpClient();
      var result = 'not null';

      http.expectGET(buildQueryString()).respond(404, {});

      service.getImage(imageName, region, credentials).then(function (results) {
        result = results;
      });

      await http.flush();

      expect(result).toBe(null);

      result = 'not null';

      http.expectGET(buildQueryString()).respond(200, []);

      service.getImage(imageName, region, credentials).then(function (results) {
        result = results;
      });

      await http.flush();

      expect(result).toBe(null);
    });
  });
});
