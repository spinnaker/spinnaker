import {module, IHttpService, ILogService, IPromise, IHttpPromiseCallbackArg} from 'angular';
import {NetflixSettings} from 'netflix/netflix.settings';

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
    return data.files[NetflixSettings.whatsNew.fileName].content;
  }

  static get $inject() { return ['$http', '$log']; }

  constructor(private $http: IHttpService, private $log: ILogService) {}

  public getWhatsNewContents(): IPromise<IWhatsNewContents> {
    const gistId = NetflixSettings.whatsNew.gistId,
      accessToken = NetflixSettings.whatsNew.accessToken || null;
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
