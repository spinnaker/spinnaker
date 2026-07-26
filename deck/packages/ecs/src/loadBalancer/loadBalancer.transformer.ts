import type { IEcsLoadBalancer, IEcsLoadBalancerSourceData, IEcsTargetGroup } from '../domain/IEcsLoadBalancer';

export class EcsLoadBalancerTransformer {
  public normalizeLoadBalancer(loadBalancer: IEcsLoadBalancerSourceData): PromiseLike<IEcsLoadBalancer> {
    loadBalancer.targetGroups.forEach((tg: IEcsTargetGroup) => {
      tg.region = loadBalancer.region;
      tg.account = loadBalancer.account;
      tg.cloudProvider = loadBalancer.cloudProvider;
      if (loadBalancer.targetGroupServices) {
        const tgServiceMap = loadBalancer.targetGroupServices;
        tg.serverGroups = tgServiceMap[tg.targetGroupArn];
      }
    });
    return Promise.resolve(loadBalancer);
  }
}
