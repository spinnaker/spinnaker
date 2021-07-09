import { REST } from '../../../../api/ApiService';

export class ArtifactoryReaderService {
  public static getArtifactoryNames(): PromiseLike<string[]> {
    return REST('/artifactory/names').get();
  }
}
