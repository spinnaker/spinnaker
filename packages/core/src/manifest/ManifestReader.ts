import { REST } from '../api/ApiService';
import { IManifest } from '../domain';

export class ManifestReader {
  public static getManifest(account: string, location: string, name: string): PromiseLike<IManifest> {
    return REST('/manifests').path(account, location, name).get();
  }
}
