import { API } from 'core/api/ApiService';

export class ArtifactService {
  public static getArtifactNames(type: string, accountName: string): PromiseLike<string[]> {
    return API.path('artifacts').path('account').path(accountName).path('names').query({ type: type }).get();
  }

  public static getArtifactVersions(type: string, accountName: string, artifactName: string): PromiseLike<string[]> {
    return API.path('artifacts')
      .path('account')
      .path(accountName)
      .path('versions')
      .query({ type: type, artifactName: artifactName })
      .get();
  }
}
