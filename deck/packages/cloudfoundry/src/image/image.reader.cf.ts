import { REST } from '@spinnaker/core';
import type { ICloudFoundryCluster } from '../domain';

export class CloudFoundryImageReader {
  public static findImages(account: string): Promise<ICloudFoundryCluster[]> {
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
