import { REST } from '../../../../api/ApiService';

export class ArtifactService {
  public static getArtifactNames(type: string, accountName: string): PromiseLike<string[]> {
    return REST('/artifacts/account').path(accountName, 'names').query({ type: type }).get();
  }

  public static getArtifactVersions(type: string, accountName: string, artifactName: string): PromiseLike<string[]> {
    return REST('/artifacts/account')
      .path(accountName, 'versions')
      .query({ type: type, artifactName: artifactName })
      .get();
  }
}
