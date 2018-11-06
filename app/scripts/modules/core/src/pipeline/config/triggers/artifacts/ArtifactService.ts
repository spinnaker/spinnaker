import { API } from 'core/api/ApiService';
import { IPromise } from 'angular';

export class ArtifactService {
  public static getArtifactNames(type: string, accountName: string): IPromise<string[]> {
    return API.one('artifacts')
      .one(type)
      .one('account')
      .one(accountName)
      .one('names')
      .get();
  }

  public static getArtifactVersions(type: string, accountName: string, chartName: string): IPromise<string[]> {
    return API.one('artifacts')
      .one(type)
      .one('account')
      .one(accountName)
      .one('names')
      .one(chartName)
      .one('versions')
      .get();
  }
}
