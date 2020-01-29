import { flatten, keyBy, isNumber } from 'lodash';
import { IAmazonApplicationLoadBalancer, IAmazonNetworkLoadBalancer, ITargetGroup, IAmazonHealth } from 'amazon/domain';

export const getAllTargetGroups = (
  loadBalancers: IAmazonApplicationLoadBalancer[] | IAmazonNetworkLoadBalancer[],
): { [name: string]: ITargetGroup } => {
  const allTargetGroups = loadBalancers.map(d => d.targetGroups);
  const targetGroups = keyBy(flatten(allTargetGroups), 'name');
  return targetGroups;
};

export const applyHealthCheckInfoToTargetGroups = (
  healthMetrics: IAmazonHealth[],
  targetGroups: { [name: string]: ITargetGroup },
) => {
  healthMetrics.forEach(metric => {
    if (metric.type === 'TargetGroup') {
      metric.targetGroups.forEach((tg: ITargetGroup) => {
        const group = targetGroups[tg.name] ?? ({} as ITargetGroup);
        const port = group.healthCheckPort && isNumber(group.healthCheckPort) ? group.healthCheckPort : group.port;
        tg.healthCheckProtocol = group.healthCheckProtocol.toLowerCase();
        tg.healthCheckPath = `:${port}${group.healthCheckPath}`;
      });
    }
  });
};
