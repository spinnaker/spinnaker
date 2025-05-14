import { REST } from '../api/ApiService';
import type { IArtifact, IManifest } from '../domain';
import { getContentReference } from '../pipeline';
import { ArtifactService } from '../pipeline/config/triggers/artifacts/ArtifactService';
import { decodeUnicodeBase64 } from '../utils';

export class ManifestReader {
  public static getManifest(account: string, location: string, name: string): PromiseLike<IManifest> {
    return REST('/manifests').path(account, location, name).get();
  }

  public static async getManifestFromArtifact(artifact: IArtifact): Promise<IManifest> {
    const manifest = await ArtifactService.getArtifactByContentReference(getContentReference(artifact.reference));
    return JSON.parse(decodeUnicodeBase64(manifest.reference));
  }
}
