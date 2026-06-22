import { module } from 'angular';
import { uniq } from 'lodash';

import type { IGceHttpLoadBalancer, IGceLoadBalancer } from '../domain/loadBalancer';

export class GceHttpLoadBalancerUtils {
  public static REGION = 'global';
  public static HTTP_LOAD_BALANCER_TYPES = ['HTTP', 'INTERNAL_MANAGED', 'EXTERNAL_MANAGED'];
  public REGION = GceHttpLoadBalancerUtils.REGION;

  public isHttpLoadBalancer(lb: IGceLoadBalancer): lb is IGceHttpLoadBalancer {
    return (
      (lb.provider === 'gce' || lb.type === 'gce') &&
      GceHttpLoadBalancerUtils.HTTP_LOAD_BALANCER_TYPES.includes(lb.loadBalancerType)
    );
  }

  public isRegionalHttpLoadBalancer(lb: IGceLoadBalancer): lb is IGceHttpLoadBalancer {
    return this.isHttpLoadBalancer(lb) && lb.loadBalancerType !== 'HTTP';
  }

  public isExternalHttpLoadBalancer(lb: IGceLoadBalancer): lb is IGceHttpLoadBalancer {
    return this.isHttpLoadBalancer(lb) && lb.loadBalancerType !== 'INTERNAL_MANAGED';
  }

  public normalizeLoadBalancerNamesForAccount(
    loadBalancerNames: string[],
    account: string,
    loadBalancers: IGceLoadBalancer[],
    region?: string,
  ): string[] {
    // Assume that loadBalancers is a list of all GCE load balancers in an application
    // (but possibly from several accounts), and has already been normalized (listener names mapped to URL map names).
    // Regional listener names are not globally unique, so account/region must participate in the match.
    const normalizedLoadBalancerNames: string[] = [];
    loadBalancerNames.forEach((loadBalancerName) => {
      const matchingUrlMap = loadBalancers.find((loadBalancer) => {
        return (
          account === loadBalancer.account &&
          this.isHttpLoadBalancer(loadBalancer) &&
          (!this.isRegionalHttpLoadBalancer(loadBalancer) || !region || loadBalancer.region === region) &&
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
