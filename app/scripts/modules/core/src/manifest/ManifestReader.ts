import { IPromise } from 'angular';
import { API } from 'core/api/ApiService';
import { IManifest } from 'core/domain';

export class ManifestReader {
  public static getManifest(account: string, location: string, name: string): IPromise<IManifest> {
    return API.all('manifests')
      .all(account)
      .all(location)
      .one(name)
      .get();
  }
}
