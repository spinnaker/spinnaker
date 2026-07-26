import { REST } from '../../../../api/ApiService';

export class ArtifactService {
  public static getArtifactNames(type: string, accountName: string): Promise<string[]> {
    return REST('/artifacts/account').path(accountName, 'names').query({ type: type }).get();
  }

  public static getArtifactVersions(type: string, accountName: string, artifactName: string): Promise<string[]> {
    return REST('/artifacts/account')
      .path(accountName, 'versions')
      .query({ type: type, artifactName: artifactName })
      .get();
  }

  public static getArtifactByContentReference(contentRef: string): Promise<{ reference: string }> {
    return REST(`/artifacts/content-address/${contentRef}`).get();
  }
}
