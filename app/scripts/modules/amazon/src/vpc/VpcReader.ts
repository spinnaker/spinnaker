import { IPromise } from 'angular';

import { $q } from 'ngimport';

import { IVpc, NetworkReader } from '@spinnaker/core';

export class VpcReader {
  private static cachedVpcs: IVpc[];

  public static listVpcs(): IPromise<IVpc[]> {
    if (this.cachedVpcs) {
      return $q.when(this.cachedVpcs);
    }
    return NetworkReader.listNetworksByProvider('aws').then((vpcs: IVpc[]) => {
      const results = vpcs.map(vpc => {
        vpc.label = vpc.name;
        vpc.deprecated = !!vpc.deprecated;
        if (vpc.deprecated) {
          vpc.label += ' (deprecated)';
        }
        return vpc;
      });
      this.cachedVpcs = results;
      return results;
    });
  }

  public static resetCache() {
    this.cachedVpcs = null;
  }

  public static getVpcName(id: string) {
    return this.listVpcs().then(vpcs => {
      const match = vpcs.find(test => {
        return test.id === id;
      });
      return match ? match.name : null;
    });
  }
}
