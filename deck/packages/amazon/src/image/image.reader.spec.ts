import { mock } from 'angular';
import { $rootScope } from 'ngimport';
// eslint-disable-next-line @spinnaker/import-from-npm-not-relative
import { mockHttpClient } from '../../../core/src/api/mock/jasmine';
import { AwsImageReader } from './image.reader';

describe('Service: aws Image Reader', function () {
  let service: AwsImageReader;

  beforeEach(() => (service = new AwsImageReader()));
  beforeEach(mock.inject());

  describe('findImages', function () {
    const region = 'us-west-2';
    const buildQueryString = (query: string, region: string) => `/images/find?q=${query}&region=${region}`;

    it('queries gate when 3 characters are supplied', async function () {
      const q = 'que';
      const http = mockHttpClient({ autoFlush: true });
      http.expectGET(buildQueryString(q, region)).respond(200, [{ success: true }]);
      const result = await service.findImages({ q, region });

      expect(result.length).toBe(1);
      expect((result[0] as any).success).toBe(true);
    });

    it('queries gate when more than 3 characters are supplied', async function () {
      const q = 'abcd';
      const http = mockHttpClient({ autoFlush: true });
      http.expectGET(buildQueryString(q, region)).respond(200, [{ success: true }]);
      const result = await service.findImages({ q, region });
      expect(result.length).toBe(1);
      expect((result[0] as any).success).toBe(true);
    });

    it('returns a message prompting user to enter more characters when less than 3 are supplied', async function () {
      const q = 'ab';
      let result: any;
      service.findImages({ q, region }).then((response) => (result = response));
      $rootScope.$digest();
      expect(result.length).toBe(1);
      expect((result[0] as any).message).toBe('Please enter at least 3 characters...');
    });

    it('returns an empty array when server errors', async function () {
      const q = 'abc';
      const http = mockHttpClient({ autoFlush: true });
      http.expectGET(buildQueryString(q, region)).respond(404, {});
      const result = await service.findImages({ q, region });
      expect(result.length).toBe(0);
    });
  });

  describe('getImage', function () {
    const imageName = 'abc';
    const region = 'us-west-1';
    const credentials = 'test';

    const buildQueryString = () => `/images/${credentials}/${region}/${imageName}?provider=aws`;

    it('returns null if server returns 404', async function () {
      const http = mockHttpClient({ autoFlush: true });
      http.expectGET(buildQueryString()).respond(404, {});
      const result = await service.getImage(imageName, region, credentials);
      expect(result).toBe(null);
    });

    it('returns null if server returns an empty list', async function () {
      const http = mockHttpClient({ autoFlush: true });
      http.expectGET(buildQueryString()).respond(200, []);
      const result2 = await service.getImage(imageName, region, credentials);
      expect(result2).toBe(null);
    });
  });
});
