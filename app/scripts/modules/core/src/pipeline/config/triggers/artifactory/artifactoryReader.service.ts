import { IPromise } from 'angular';

import { API } from 'core/api/ApiService';

export class ArtifactoryReaderService {
  public static getArtifactoryNames(): IPromise<string[]> {
    return API.one('artifactory')
      .one('names')
      .get();
  }
}
