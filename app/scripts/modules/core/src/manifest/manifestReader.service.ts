import { IPromise, module } from 'angular';

import { API } from 'core/api/ApiService';
import { IManifest } from 'core/domain';

export class ManifestReader {
  public getManifest(account: string, location: string, name: string): IPromise<IManifest> {
    return API.all('manifests')
      .all(account)
      .all(location)
      .one(name)
      .get();
  }
}

export const MANIFEST_READER = 'spinnaker.core.manifest.read.service';
module(MANIFEST_READER, []).service('manifestReader', ManifestReader);
