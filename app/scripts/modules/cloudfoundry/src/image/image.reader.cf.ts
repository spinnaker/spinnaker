import { API } from '@spinnaker/core';

import { ICloudFoundryCluster } from 'cloudfoundry/domain';

export class CloudFoundryImageReader {
  public static findImages(account: string): PromiseLike<ICloudFoundryCluster[]> {
    return API.one('images', 'find')
      .withParams({
        account,
        provider: 'cloudfoundry',
      })
      .get()
      .then(function (results: any) {
        return results;
      })
      .catch((): any[] => []);
  }
}
