import { module } from 'angular';
import { cloneDeep, Dictionary, groupBy, map, partition } from 'lodash';

import { GCE_HTTP_LOAD_BALANCER_UTILS, GceHttpLoadBalancerUtils } from 'google/loadBalancer/httpLoadBalancerUtils.service';
import { IGceListener, IGceLoadBalancer, IGceHttpLoadBalancer } from 'google/domain/loadBalancer';
import { InternalLoadBalancer } from 'google/loadBalancer/configure/internal/gceCreateInternalLoadBalancer.controller';

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
        ports: null,
        subnet: null
      };
    });

    normalized.name = normalized.urlMapName;
    return normalized;
  }

  private static normalizeInternalLoadBalancerGroup(group: InternalLoadBalancer[]): InternalLoadBalancer {
    const normalized = cloneDeep(group[0]);

    normalized.listeners = group.map((loadBalancer) => {
      return {
        ports: loadBalancer.ports,
        name: loadBalancer.name,
        subnet: loadBalancer.subnet
      } as IGceListener;
    });

    normalized.name = normalized.backendService.name;
    return normalized as InternalLoadBalancer;
  }

  private static parsePortRange (portRange: string): string {
    return portRange.split('-')[0];
  }

  constructor(private gceHttpLoadBalancerUtils: GceHttpLoadBalancerUtils) { 'ngInject'; }

  public normalizeLoadBalancerSet = (loadBalancers: IGceLoadBalancer[]): IGceLoadBalancer[] => {
    const [httpLoadBalancers, otherLoadBalancers] = partition(loadBalancers, lb => this.gceHttpLoadBalancerUtils.isHttpLoadBalancer(lb));
    const [internalLoadBalancers, nonHttpOrInternalLoadBalancers] = partition(otherLoadBalancers, lb =>
        (lb.provider === 'gce' || lb.type === 'gce') && lb.loadBalancerType === 'INTERNAL'
    );

    const groupedByUrlMap = groupBy(httpLoadBalancers, 'urlMapName');
    const normalizedElSevenLoadBalancers = map(groupedByUrlMap, GceLoadBalancerSetTransformer.normalizeHttpLoadBalancerGroup);
    const groupedByBackendService = groupBy(internalLoadBalancers, 'backendService') as Dictionary<InternalLoadBalancer[]>;
    const normalizedInternalLoadBalancers: InternalLoadBalancer[] = map(groupedByBackendService, GceLoadBalancerSetTransformer.normalizeInternalLoadBalancerGroup);

    return (normalizedElSevenLoadBalancers as IGceLoadBalancer[]).concat(normalizedInternalLoadBalancers as IGceLoadBalancer[]).concat(nonHttpOrInternalLoadBalancers);
  }
}

export const LOAD_BALANCER_SET_TRANSFORMER = 'spinnaker.gce.loadBalancer.setTransformer.service';
module(LOAD_BALANCER_SET_TRANSFORMER, [GCE_HTTP_LOAD_BALANCER_UTILS])
  .service('gceLoadBalancerSetTransformer', GceLoadBalancerSetTransformer);
