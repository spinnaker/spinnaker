import { module } from 'angular';
import { cloneDeep, groupBy, map, partition } from 'lodash';

import { IGceHttpLoadBalancer, IGceLoadBalancer } from '../domain/loadBalancer';

import { GCE_HTTP_LOAD_BALANCER_UTILS, GceHttpLoadBalancerUtils } from './httpLoadBalancerUtils.service';

export class GceLoadBalancerSetTransformer {
  private static normalizeHttpLoadBalancerGroup(group: IGceHttpLoadBalancer[]): IGceHttpLoadBalancer {
    const normalized = cloneDeep(group[0]);

    normalized.listeners = group.map((loadBalancer) => {
      const port = loadBalancer.portRange ? GceLoadBalancerSetTransformer.parsePortRange(loadBalancer.portRange) : null;
      return {
        port,
        name: loadBalancer.name,
        certificate: loadBalancer.certificate,
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

  public static $inject = ['gceHttpLoadBalancerUtils'];
  constructor(private gceHttpLoadBalancerUtils: GceHttpLoadBalancerUtils) {}

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
module(LOAD_BALANCER_SET_TRANSFORMER, [GCE_HTTP_LOAD_BALANCER_UTILS]).service(
  'gceLoadBalancerSetTransformer',
  GceLoadBalancerSetTransformer,
);
