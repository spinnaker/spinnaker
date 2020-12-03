import { API } from '@spinnaker/core';

import { ICloudFoundryCluster } from 'cloudfoundry/domain';

export class CloudFoundryImageReader {
  public static findImages(account: string): PromiseLike<ICloudFoundryCluster[]> {
    return API.path('images', 'find')
      .query({
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
