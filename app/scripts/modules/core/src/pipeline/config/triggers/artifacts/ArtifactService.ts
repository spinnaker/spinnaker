import { API } from 'core/api/ApiService';
import { IPromise } from 'angular';

export class ArtifactService {
  public static getArtifactNames(type: string, accountName: string): IPromise<string[]> {
    return API.one('artifacts')
      .one('account')
      .one(accountName)
      .one('names')
      .withParams({ type: type })
      .get();
  }

  public static getArtifactVersions(type: string, accountName: string, artifactName: string): IPromise<string[]> {
    return API.one('artifacts')
      .one('account')
      .one(accountName)
      .one('versions')
      .withParams({ type: type, artifactName: artifactName })
      .get();
  }
}
