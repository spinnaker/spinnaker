import { REST } from 'core/api/ApiService';

export class ArtifactoryReaderService {
  public static getArtifactoryNames(): PromiseLike<string[]> {
    return REST('/artifactory/names').get();
  }
}
