import { mock, IHttpBackendService } from 'angular';
import { SETTINGS } from 'core/config/settings';
import { WhatsNewReader, IGistApiResponse, IWhatsNewContents } from './WhatsNewReader';

describe('Service: whatsNew reader ', () => {
  let $http: IHttpBackendService;

  beforeEach(
    mock.inject(($httpBackend: IHttpBackendService) => {
      $http = $httpBackend;
    }),
  );

  beforeEach(() => {
    SETTINGS.changelog = { gistId: 'abc', fileName: 'log.md' };
  });

  afterEach(SETTINGS.resetToOriginal);

  describe('getWhatsNewContents', () => {
    let url: string;
    beforeEach(() => {
      const gistId = SETTINGS.changelog.gistId;
      url = `https://api.github.com/gists/${gistId}`;
    });

    it('returns file contents with lastUpdated', () => {
      let result: IWhatsNewContents = null;
      const response: IGistApiResponse = {
        updated_at: '1999',
        files: {},
      };

      response.files[SETTINGS.changelog.fileName] = {
        content: 'expected content',
      };

      $http.expectGET(url).respond(200, response);

      WhatsNewReader.getWhatsNewContents().then(data => (result = data));
      $http.flush();

      expect(result).not.toBeNull();
      expect(result.lastUpdated).toBe('1999');
      expect(result.contents).toBe('expected content');
    });

    it('returns null when gist fetch fails', () => {
      let result: IWhatsNewContents = { contents: 'fail', lastUpdated: 'never' };
      $http.expectGET(url).respond(404, {});
      WhatsNewReader.getWhatsNewContents().then(data => (result = data));
      $http.flush();

      expect(result).toBeNull();
    });
  });
});
