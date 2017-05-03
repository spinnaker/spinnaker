import {mock, IHttpBackendService} from 'angular';
import {NetflixSettings} from 'netflix/netflix.settings';
import {SETTINGS} from 'core/config/settings';
import {WHATS_NEW_READ_SERVICE, WhatsNewReader, IGistApiResponse, IWhatsNewContents} from './whatsNew.read.service';

describe('Service: whatsNew reader ', () => {
  let reader: WhatsNewReader;
  let $http: IHttpBackendService;

  beforeEach(
    mock.module(
      WHATS_NEW_READ_SERVICE
    )
  );

  beforeEach(mock.inject((whatsNewReader: WhatsNewReader, $httpBackend: IHttpBackendService) => {
    reader = whatsNewReader;
    $http = $httpBackend;
  }));

  beforeEach(() => { SETTINGS.feature.netflixMode = true; });

  afterEach(SETTINGS.resetToOriginal);

  describe('getWhatsNewContents', () => {
    let url: string;
    beforeEach(() => {
      const gistId = NetflixSettings.whatsNew.gistId;
      url = `https://api.github.com/gists/${gistId}`;
    });

    it ('returns file contents with lastUpdated', () => {
      let result: IWhatsNewContents = null;
      const response: IGistApiResponse = {
          'updated_at': '1999',
          files: {},
        };

      response.files[NetflixSettings.whatsNew.fileName] = {
        content: 'expected content',
      };

      $http.expectGET(url).respond(200, response);

      reader.getWhatsNewContents().then((data: IWhatsNewContents) => result = data);
      $http.flush();

      expect(result).not.toBeNull();
      expect(result.lastUpdated).toBe('1999');
      expect(result.contents).toBe('expected content');
    });

    it('returns null when gist fetch fails', () => {
      let result: IWhatsNewContents = {contents: 'fail', lastUpdated: 'never'};
      $http.expectGET(url).respond(404, {});
      reader.getWhatsNewContents().then(function(data) {
        result = data;
      });
      $http.flush();

      expect(result).toBeNull();
    });
  });
});
