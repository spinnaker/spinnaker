import { module } from 'angular';
import { uniq } from 'lodash';

import { IGceHttpLoadBalancer, IGceLoadBalancer } from '../domain/loadBalancer';

export class GceHttpLoadBalancerUtils {
  public static REGION = 'global';

  public isHttpLoadBalancer(lb: IGceLoadBalancer): lb is IGceHttpLoadBalancer {
    return (
      (lb.provider === 'gce' || lb.type === 'gce') &&
      (lb.loadBalancerType === 'HTTP' || lb.loadBalancerType === 'INTERNAL_MANAGED')
    );
  }

  public normalizeLoadBalancerNamesForAccount(
    loadBalancerNames: string[],
    account: string,
    loadBalancers: IGceLoadBalancer[],
  ): string[] {
    // Assume that loadBalancers is a list of all GCE load balancers in an application
    // (but possibly from several accounts), and has already been normalized (listener names mapped to URL map names).
    const normalizedLoadBalancerNames: string[] = [];
    loadBalancerNames.forEach((loadBalancerName) => {
      const matchingUrlMap = loadBalancers.find((loadBalancer) => {
        return (
          account === loadBalancer.account &&
          this.isHttpLoadBalancer(loadBalancer) &&
          loadBalancer.listeners.map((listener) => listener.name).includes(loadBalancerName)
        );
      });

      matchingUrlMap
        ? normalizedLoadBalancerNames.push(matchingUrlMap.name)
        : normalizedLoadBalancerNames.push(loadBalancerName);
    });
    return uniq(normalizedLoadBalancerNames);
  }
}

export const GCE_HTTP_LOAD_BALANCER_UTILS = 'spinnaker.gce.httpLoadBalancerUtils.service';
module(GCE_HTTP_LOAD_BALANCER_UTILS, []).service('gceHttpLoadBalancerUtils', GceHttpLoadBalancerUtils);
