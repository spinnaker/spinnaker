import { REST } from 'core/api/ApiService';
import { IManifest } from 'core/domain';

export class ManifestReader {
  public static getManifest(account: string, location: string, name: string): PromiseLike<IManifest> {
    return REST('/manifests').path(account, location, name).get();
  }
}
