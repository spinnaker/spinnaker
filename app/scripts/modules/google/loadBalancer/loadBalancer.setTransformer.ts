import {module} from 'angular';
import * as _ from 'lodash';

export class GceLoadBalancerSetTransformer {

  static get $inject() { return ['elSevenUtils']; }

  constructor(private elSevenUtils: any) {}

  public normalizeLoadBalancerSet = (loadBalancers: any[]): any[] => {
    let [elSevenLoadBalancers, otherLoadBalancers] = _.partition(loadBalancers, this.elSevenUtils.isElSeven);

    let groupedByUrlMap = _.groupBy(elSevenLoadBalancers, 'urlMapName');
    let normalizedElSevenLoadBalancers = _.map(groupedByUrlMap, this.normalizeElSevenGroup);

    return normalizedElSevenLoadBalancers.concat(otherLoadBalancers);
  };

  private normalizeElSevenGroup = (group: any[]): any[] => {
    let normalized = _.cloneDeep(group[0]);

    normalized.listeners = group.map((loadBalancer) => {
      let port = loadBalancer.portRange ? this.parsePortRange(loadBalancer.portRange) : null;
      return {
        port,
        name: loadBalancer.name,
        certificate: loadBalancer.certificate,
      };
    });

    normalized.name = normalized.urlMapName;
    return normalized;
  };

  private parsePortRange = (portRange: string): string => {
    return portRange.split('-')[0];
  };
}

const moduleName = 'spinnaker.gce.loadBalancer.setTransformer.service';

module(moduleName, [require('./elSevenUtils.service.js')])
  .service('gceLoadBalancerSetTransformer', GceLoadBalancerSetTransformer);

export default moduleName;
