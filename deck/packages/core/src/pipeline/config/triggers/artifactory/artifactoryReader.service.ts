import { REST } from '../../../../api/ApiService';

export class ArtifactoryReaderService {
  public static getArtifactoryNames(): Promise<string[]> {
    return REST('/artifactory/names').get();
  }
}
