import { API } from 'core/api/ApiService';

export class ArtifactoryReaderService {
  public static getArtifactoryNames(): PromiseLike<string[]> {
    return API.one('artifactory').one('names').get();
  }
}
