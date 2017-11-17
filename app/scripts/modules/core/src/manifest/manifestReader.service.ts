import { IPromise, module } from 'angular';

import { Api, API_SERVICE } from 'core/api/api.service';
import { IManifest } from 'core/domain';

export class ManifestReader {

  constructor(private API: Api) { 'ngInject'; }

  public getManifest(account: string,
                     location: string,
                     name: string): IPromise<IManifest> {
    return this.API.all('manifests')
      .all(account)
      .all(location)
      .one(name)
      .get();
  }
}

export const MANIFEST_READER = 'spinnaker.core.manifest.read.service';
module(MANIFEST_READER, [API_SERVICE])
  .service('manifestReader', ManifestReader);
