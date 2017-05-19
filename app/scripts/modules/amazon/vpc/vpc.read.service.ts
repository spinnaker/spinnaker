import { module, IQService, IPromise } from 'angular';

import { IVpc, NETWORK_READ_SERVICE, NetworkReader } from '@spinnaker/core';

export class VpcReader {

  private cachedVpcs: IVpc[];

  constructor(private $q: IQService, private networkReader: NetworkReader) {
    'ngInject';
  }

  public listVpcs(): IPromise<IVpc[]> {
    if (this.cachedVpcs) {
      return this.$q.when(this.cachedVpcs);
    }
    return this.networkReader.listNetworksByProvider('aws').then((vpcs: IVpc[]) => {
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

  public resetCache() {
    this.cachedVpcs = null;
  }

  public getVpcName(id: string) {
    return this.listVpcs().then(vpcs => {
      const match = vpcs.find(test => {
        return test.id === id;
      });
      return match ? match.name : null;
    });
  }
}

export const VPC_READ_SERVICE = 'spinnaker.amazon.vpc.read.service';
module(VPC_READ_SERVICE, [NETWORK_READ_SERVICE]).service('vpcReader', VpcReader);
