import {cloneDeep, groupBy, map, partition} from 'lodash';
import {module} from 'angular';

export class GceLoadBalancerSetTransformer {

  private static normalizeElSevenGroup(group: any[]): any[] {
    let normalized = cloneDeep(group[0]);

    normalized.listeners = group.map((loadBalancer) => {
      let port = loadBalancer.portRange ? GceLoadBalancerSetTransformer.parsePortRange(loadBalancer.portRange) : null;
      return {
        port,
        name: loadBalancer.name,
        certificate: loadBalancer.certificate,
      };
    });

    normalized.name = normalized.urlMapName;
    return normalized;
  }

  private static parsePortRange (portRange: string): string {
    return portRange.split('-')[0];
  }

  static get $inject() {
    return ['elSevenUtils'];
  }

  constructor(private elSevenUtils: any) {}

  public normalizeLoadBalancerSet = (loadBalancers: any[]): any[] => {
    let [elSevenLoadBalancers, otherLoadBalancers] = partition(loadBalancers, this.elSevenUtils.isElSeven);

    let groupedByUrlMap = groupBy(elSevenLoadBalancers, 'urlMapName');
    let normalizedElSevenLoadBalancers = map(groupedByUrlMap, GceLoadBalancerSetTransformer.normalizeElSevenGroup);

    return normalizedElSevenLoadBalancers.concat(otherLoadBalancers);
  };
}

export const LOAD_BALANCER_SET_TRANSFORMER = 'spinnaker.gce.loadBalancer.setTransformer.service';
module(LOAD_BALANCER_SET_TRANSFORMER, [require('./elSevenUtils.service.js')])
  .service('gceLoadBalancerSetTransformer', GceLoadBalancerSetTransformer);
