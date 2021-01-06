import { flatten, isNil } from 'lodash';
import { IAmazonApplicationLoadBalancer, IAmazonNetworkLoadBalancer, ITargetGroup, IAmazonHealth } from 'amazon/domain';

export const getAllTargetGroups = (
  loadBalancers: IAmazonApplicationLoadBalancer[] | IAmazonNetworkLoadBalancer[],
): ITargetGroup[] => {
  const allTargetGroups = loadBalancers.map((d) => d.targetGroups);
  const targetGroups = flatten(allTargetGroups);
  return targetGroups;
};

export const applyHealthCheckInfoToTargetGroups = (
  healthMetrics: IAmazonHealth[],
  targetGroups: ITargetGroup[],
  account: string,
) => {
  healthMetrics.forEach((metric) => {
    if (metric.type === 'TargetGroup') {
      metric.targetGroups.forEach((tg) => {
        const group = targetGroups.find((g) => g.name === tg.name && g.account === account) ?? ({} as ITargetGroup);
        const useTrafficPort = group.healthCheckPort === 'traffic-port' || isNil(group.healthCheckPort);
        const port = useTrafficPort ? group.port : group.healthCheckPort;
        tg.healthCheckProtocol = group.healthCheckProtocol?.toLowerCase();
        tg.healthCheckPath = `:${port}${group.healthCheckPath ?? ''}`;
      });
    }
  });
};
