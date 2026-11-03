import { uniq } from 'lodash';

import type { IGceHttpLoadBalancer, IGceLoadBalancer } from '../domain/loadBalancer';

export class GceHttpLoadBalancerUtils {
  public static REGION = 'global';
  public static HTTP_LOAD_BALANCER_TYPES = ['HTTP', 'INTERNAL_MANAGED', 'EXTERNAL_MANAGED'];

  public isHttpLoadBalancer(lb: IGceLoadBalancer): lb is IGceHttpLoadBalancer {
    const loadBalancer = lb as any;
    return (
      (loadBalancer.provider === 'gce' || loadBalancer.type === 'gce') &&
      GceHttpLoadBalancerUtils.HTTP_LOAD_BALANCER_TYPES.includes(loadBalancer.loadBalancerType)
    );
  }

  public isRegionalHttpLoadBalancer(lb: IGceLoadBalancer): lb is IGceHttpLoadBalancer {
    return this.isHttpLoadBalancer(lb) && lb.loadBalancerType !== 'HTTP';
  }

  public normalizeLoadBalancerNamesForAccount(
    loadBalancerNames: string[],
    account: string,
    loadBalancers: IGceLoadBalancer[],
    region?: string,
  ): string[] {
    // Assume that loadBalancers is a list of all GCE load balancers in an application
    // (but possibly from several accounts), and has already been normalized (listener names mapped to URL map names).
    const normalizedLoadBalancerNames: string[] = [];
    const hasListener = (loadBalancer: IGceLoadBalancer, loadBalancerName: string) =>
      account === loadBalancer.account &&
      this.isHttpLoadBalancer(loadBalancer) &&
      loadBalancer.listeners.map((listener) => listener.name).includes(loadBalancerName);
    loadBalancerNames.forEach((loadBalancerName) => {
      const matchingUrlMap = loadBalancers.find(
        (loadBalancer) =>
          loadBalancer.loadBalancerType !== 'EXTERNAL_MANAGED' && hasListener(loadBalancer, loadBalancerName),
      );
      // EXTERNAL_MANAGED listener names repeat across regions, so a raw alias maps only when the
      // account and region identify one load balancer.
      const externalMatches = matchingUrlMap
        ? []
        : loadBalancers.filter(
            (loadBalancer) =>
              loadBalancer.loadBalancerType === 'EXTERNAL_MANAGED' &&
              (!region || loadBalancer.region === region) &&
              hasListener(loadBalancer, loadBalancerName),
          );
      const match = matchingUrlMap || (externalMatches.length === 1 ? externalMatches[0] : undefined);

      match ? normalizedLoadBalancerNames.push(match.name) : normalizedLoadBalancerNames.push(loadBalancerName);
    });
    return uniq(normalizedLoadBalancerNames);
  }
}
