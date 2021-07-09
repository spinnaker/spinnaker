import { REST } from '@spinnaker/core';
import { ICloudFoundryCluster } from '../domain';

export class CloudFoundryImageReader {
  public static findImages(account: string): PromiseLike<ICloudFoundryCluster[]> {
    return REST('/images/find')
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
