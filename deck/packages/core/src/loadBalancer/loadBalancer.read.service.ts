import { REST } from '../api/ApiService';
import type { ILoadBalancer, ILoadBalancerSourceData } from '../domain';
import type { IComponentName } from '../naming';
import { NameUtils } from '../naming';
import type { PromiseService } from '../utils/nativePromiseService';

export interface ILoadBalancersByAccount {
  name: string;
  accounts: Array<{
    name: string;
    regions: Array<{
      name: string;
      loadBalancers: ILoadBalancerSourceData[];
    }>;
  }>;
}

export class LoadBalancerReader {
  public constructor(private promiseService: PromiseService, private loadBalancerTransformer: any) {}

  public loadLoadBalancers(applicationName: string): Promise<ILoadBalancerSourceData[]> {
    return REST('/applications')
      .path(applicationName, 'loadBalancers')
      .get()
      .then((loadBalancers: ILoadBalancerSourceData[]) => {
        loadBalancers = this.loadBalancerTransformer.normalizeLoadBalancerSet(loadBalancers);
        return this.promiseService.all(loadBalancers.map((lb) => this.normalizeLoadBalancer(lb)));
      });
  }

  public getLoadBalancerDetails(
    cloudProvider: string,
    account: string,
    region: string,
    name: string,
  ): Promise<ILoadBalancerSourceData[]> {
    return REST('/loadBalancers').path(account, region, name).query({ provider: cloudProvider }).get();
  }

  public listLoadBalancers(cloudProvider: string): Promise<ILoadBalancersByAccount[]> {
    return REST('/loadBalancers').query({ provider: cloudProvider }).get();
  }

  private normalizeLoadBalancer(loadBalancer: ILoadBalancerSourceData): Promise<ILoadBalancer> {
    return this.loadBalancerTransformer.normalizeLoadBalancer(loadBalancer).then((lb: ILoadBalancer) => {
      const nameParts: IComponentName = NameUtils.parseLoadBalancerName(lb.name);
      lb.stack = nameParts.stack;
      lb.detail = nameParts.freeFormDetails;
      lb.cloudProvider = lb.cloudProvider || lb.type || lb.provider;
      return lb;
    });
  }
}
