import { module } from 'angular';
import { cloneDeep, groupBy, map, partition } from 'lodash';

import type { IGceHttpLoadBalancer, IGceLoadBalancer } from '../domain/loadBalancer';

import type { GceHttpLoadBalancerUtils } from './httpLoadBalancerUtils.service';
import { GCE_HTTP_LOAD_BALANCER_UTILS } from './httpLoadBalancerUtils.service';

export class GceLoadBalancerSetTransformer {
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
        : // Regional URL map names can repeat across accounts and regions; include both in the
          // display identity while details routing can still use the raw urlMapName plus scope.
          `${normalized.urlMapName} (${normalized.account}/${normalized.region})`;
    delete normalized.subnet;
    return normalized;
  }

  private static parsePortRange(portRange: string): string {
    return portRange.split('-')[0];
  }

  public static $inject = ['gceHttpLoadBalancerUtils'];
  constructor(private gceHttpLoadBalancerUtils: GceHttpLoadBalancerUtils) {}

  public normalizeLoadBalancerSet = (loadBalancers: IGceLoadBalancer[]): IGceLoadBalancer[] => {
    const [httpLoadBalancers, otherLoadBalancers] = partition(loadBalancers, (lb) =>
      this.gceHttpLoadBalancerUtils.isHttpLoadBalancer(lb),
    );

    const groupedByUrlMap = groupBy(httpLoadBalancers, (loadBalancer) =>
      [loadBalancer.account, loadBalancer.region, loadBalancer.urlMapName].join(':'),
    );
    const normalizedElSevenLoadBalancers = map(
      groupedByUrlMap,
      GceLoadBalancerSetTransformer.normalizeHttpLoadBalancerGroup,
    );

    return (normalizedElSevenLoadBalancers as IGceLoadBalancer[]).concat(otherLoadBalancers);
  };
}

export const LOAD_BALANCER_SET_TRANSFORMER = 'spinnaker.gce.loadBalancer.setTransformer.service';
export const name = LOAD_BALANCER_SET_TRANSFORMER; // for backwards compatibility
module(LOAD_BALANCER_SET_TRANSFORMER, [GCE_HTTP_LOAD_BALANCER_UTILS]).service(
  'gceLoadBalancerSetTransformer',
  GceLoadBalancerSetTransformer,
);
