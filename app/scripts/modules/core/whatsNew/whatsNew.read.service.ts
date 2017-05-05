import {module, IHttpService, ILogService, IPromise, IHttpPromiseCallbackArg, IQService} from 'angular';
import {NetflixSettings} from 'netflix/netflix.settings';
import {SETTINGS} from 'core/config/settings';

export interface IGistApiResponse {
  // There are many other fields in the real response object.
  files: {[fileKey: string]: IGistFile};
  updated_at: string;
}

export interface IGistFile {
  content: string;
  filename?: string;
  language?: string;
  raw_url?: string;
  size?: number;
  truncated?: boolean;
  type?: string;
}

export interface IWhatsNewContents {
  contents: string;
  lastUpdated: string;
}

export class WhatsNewReader {

  private static extractFileContent(data: IGistApiResponse) {
    const fileName = SETTINGS.feature.netflixMode ? NetflixSettings.whatsNew.fileName : SETTINGS.changelog.fileName;
    return data.files[fileName].content;
  }

  constructor(private $http: IHttpService, private $log: ILogService, private $q: IQService) { 'ngInject'; }

  public getWhatsNewContents(): IPromise<IWhatsNewContents> {
    let gistId: string, accessToken: string;
    if (SETTINGS.feature.netflixMode) {
      gistId = NetflixSettings.whatsNew.gistId;
      accessToken = NetflixSettings.whatsNew.accessToken || null;
    } else {
      gistId = SETTINGS.changelog ? SETTINGS.changelog.gistId : null;
    }
    if (!gistId) {
      return this.$q.resolve(null);
    }

    let url = `https://api.github.com/gists/${gistId}`;
    if (accessToken) {
      url += '?access_token=' + accessToken;
    }
    return this.$http.get(url)
      .then((result: IHttpPromiseCallbackArg<IGistApiResponse>) => {
        return {
          contents: WhatsNewReader.extractFileContent(result.data),
          lastUpdated: result.data.updated_at,
        };
      })
      .catch((failure: IHttpPromiseCallbackArg<any>) => {
        this.$log.warn(`failed to retrieve gist for what's new dialog:`, failure);
        return null;
      });
  }
}

export const WHATS_NEW_READ_SERVICE = 'spinnaker.core.whatsNew.read.service';
module(WHATS_NEW_READ_SERVICE, [])
  .service('whatsNewReader', WhatsNewReader);
