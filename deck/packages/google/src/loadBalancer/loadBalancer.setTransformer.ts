import { cloneDeep, groupBy, map, partition } from 'lodash';

import type { IGceHttpLoadBalancer, IGceLoadBalancer } from '../domain/loadBalancer';

import { GceHttpLoadBalancerUtils } from './httpLoadBalancerUtils.service';

export class GceLoadBalancerSetTransformer {
  private readonly gceHttpLoadBalancerUtils = new GceHttpLoadBalancerUtils();

  private static normalizeHttpLoadBalancerGroup(group: IGceHttpLoadBalancer[]): IGceHttpLoadBalancer {
    const normalized = cloneDeep(group[0]);

    // Clouddriver returns one row per forwarding-rule listener. Deck presents the URL map as the
    // logical HTTP(S) load balancer, so the normalized object keeps the URL-map identity and folds
    // forwarding-rule-specific fields into listener rows.
    normalized.listeners = group.map((loadBalancer) => {
      const port = loadBalancer.portRange ? GceLoadBalancerSetTransformer.parsePortRange(loadBalancer.portRange) : null;
      return {
        port,
        name: loadBalancer.name,
        certificate: loadBalancer.certificate,
        certificateMap: loadBalancer.certificateMap,
        ipAddress: loadBalancer.ipAddress,
        networkTier: loadBalancer.networkTier,
        subnet: loadBalancer.subnet,
      };
    });

    normalized.name =
      normalized.loadBalancerType === 'HTTP'
        ? normalized.urlMapName
        : // Regional URL map names can repeat across accounts, regions, and managed schemes; include
          // all three while details routing can still use the raw urlMapName plus scope.
          `${normalized.urlMapName} (${normalized.account}/${normalized.region}/${normalized.loadBalancerType})`;
    delete normalized.subnet;
    return normalized;
  }

  private static parsePortRange(portRange: string): string {
    return portRange.split('-')[0];
  }

  public normalizeLoadBalancerSet = (loadBalancers: IGceLoadBalancer[]): IGceLoadBalancer[] => {
    const [httpLoadBalancers, otherLoadBalancers] = partition(loadBalancers, (lb) =>
      this.gceHttpLoadBalancerUtils.isHttpLoadBalancer(lb),
    );

    const groupedByUrlMap = groupBy(httpLoadBalancers, (loadBalancer) =>
      [loadBalancer.account, loadBalancer.region, loadBalancer.loadBalancerType, loadBalancer.urlMapName].join(':'),
    );
    const normalizedElSevenLoadBalancers = map(
      groupedByUrlMap,
      GceLoadBalancerSetTransformer.normalizeHttpLoadBalancerGroup,
    );

    return (normalizedElSevenLoadBalancers as IGceLoadBalancer[]).concat(otherLoadBalancers);
  };
}
