import { mockHttpClient } from 'core/api/mock/jasmine';

import { dcosImageReader } from './image.reader';

describe('Service: DCOS Image Reader', function () {
  describe('findImages', function () {
    var query = 'abc',
      region = 'usw';

    const buildQueryString = () => `/images/find?provider=dcos&q=${query}&region=${region}`;

    it('queries gate when 3 characters are supplied', async function () {
      const http = mockHttpClient();
      var result = null;

      http.expectGET(buildQueryString()).respond(200, [{ success: true }]);

      dcosImageReader.findImages({ provider: 'dcos', q: query, region: region }).then(function (results) {
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

      var promise = dcosImageReader.findImages({ provider: 'dcos', q: query, region: region });

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

      dcosImageReader.findImages({ provider: 'dcos', q: query, region: region }).then(function (results) {
        result = results;
      });

      await http.flush();

      expect(result.length).toBe(0);
    });
  });
});
