import { API } from 'core/api/ApiService';
import { IManifest } from 'core/domain';

export class ManifestReader {
  public static getManifest(account: string, location: string, name: string): PromiseLike<IManifest> {
    return API.path('manifests').path(account).path(location).path(name).get();
  }
}
