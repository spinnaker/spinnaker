import { API } from 'core/api/ApiService';

export class ArtifactoryReaderService {
  public static getArtifactoryNames(): PromiseLike<string[]> {
    return API.path('artifactory').path('names').get();
  }
}
