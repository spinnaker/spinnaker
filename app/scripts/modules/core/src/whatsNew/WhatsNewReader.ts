import { IPromise, IHttpPromiseCallbackArg } from 'angular';

import { $http, $log, $q } from 'ngimport';

import { SETTINGS } from 'core/config/settings';

export interface IGistApiResponse {
  // There are many other fields in the real response object.
  files: { [fileKey: string]: IGistFile };
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
    const fileName = SETTINGS.changelog.fileName;
    return data.files[fileName].content;
  }

  public static getWhatsNewContents(): IPromise<IWhatsNewContents> {
    const gistId: string = SETTINGS.changelog ? SETTINGS.changelog.gistId : null;
    const accessToken: string = SETTINGS.changelog ? SETTINGS.changelog.accessToken : null;
    if (!gistId) {
      return $q.resolve(null);
    }

    let url = `https://api.github.com/gists/${gistId}`;
    if (accessToken) {
      url += '?access_token=' + accessToken;
    }
    return $http
      .get(url)
      .then((result: IHttpPromiseCallbackArg<IGistApiResponse>) => {
        return {
          contents: WhatsNewReader.extractFileContent(result.data),
          lastUpdated: result.data.updated_at,
        };
      })
      .catch((failure: IHttpPromiseCallbackArg<any>) => {
        $log.warn(`failed to retrieve gist for what's new dialog:`, failure);
        return null;
      });
  }
}
