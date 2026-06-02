import { cloneDeep, groupBy, map, partition } from 'lodash';

import type { IGceHttpLoadBalancer, IGceLoadBalancer } from '../domain/loadBalancer';

import { GceHttpLoadBalancerUtils } from './httpLoadBalancerUtils.service';

export class GceLoadBalancerSetTransformer {
  private gceHttpLoadBalancerUtils: GceHttpLoadBalancerUtils;

  private static normalizeHttpLoadBalancerGroup(group: IGceHttpLoadBalancer[]): IGceHttpLoadBalancer {
    const normalized = cloneDeep(group[0]);

    normalized.listeners = group.map((loadBalancer) => {
      const port = loadBalancer.portRange ? GceLoadBalancerSetTransformer.parsePortRange(loadBalancer.portRange) : null;
      return {
        port,
        name: loadBalancer.name,
        certificate: loadBalancer.certificate,
        certificateMap: loadBalancer.certificateMap,
        ipAddress: loadBalancer.ipAddress,
        subnet: loadBalancer.subnet,
      };
    });

    normalized.name = normalized.urlMapName;
    delete normalized.subnet;
    return normalized;
  }

  private static parsePortRange(portRange: string): string {
    return portRange.split('-')[0];
  }

  constructor(gceHttpLoadBalancerUtils: GceHttpLoadBalancerUtils | unknown = new GceHttpLoadBalancerUtils()) {
    this.gceHttpLoadBalancerUtils =
      typeof (gceHttpLoadBalancerUtils as GceHttpLoadBalancerUtils).isHttpLoadBalancer === 'function'
        ? (gceHttpLoadBalancerUtils as GceHttpLoadBalancerUtils)
        : new GceHttpLoadBalancerUtils();
  }

  public normalizeLoadBalancerSet = (loadBalancers: IGceLoadBalancer[]): IGceLoadBalancer[] => {
    const [httpLoadBalancers, otherLoadBalancers] = partition(loadBalancers, (lb) =>
      this.gceHttpLoadBalancerUtils.isHttpLoadBalancer(lb),
    );

    const groupedByUrlMap = groupBy(httpLoadBalancers, 'urlMapName');
    const normalizedElSevenLoadBalancers = map(
      groupedByUrlMap,
      GceLoadBalancerSetTransformer.normalizeHttpLoadBalancerGroup,
    );

    return (normalizedElSevenLoadBalancers as IGceLoadBalancer[]).concat(otherLoadBalancers);
  };
}

export const LOAD_BALANCER_SET_TRANSFORMER = 'spinnaker.gce.loadBalancer.setTransformer.service';
