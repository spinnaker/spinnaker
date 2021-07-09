import { flatten } from 'lodash';
import { $q } from 'ngimport';

import { AccountService, Application, ILoadBalancer } from '@spinnaker/core';
import { IAmazonApplicationLoadBalancer, IAmazonHealth, IAmazonServerGroup, ITargetGroup } from '../domain';

export class AmazonLoadBalancerDataUtils {
  private static buildTargetGroup(match: ITargetGroup, serverGroup: IAmazonServerGroup): ITargetGroup {
    if (!match) {
      return null;
    }

    const targetGroup: ITargetGroup = {
      name: match.name,
      vpcId: match.vpcId,
      cloudProvider: match.cloudProvider,
      account: match.account,
      region: match.region,
      loadBalancerNames: match.loadBalancerNames,
    } as ITargetGroup;
    targetGroup.instanceCounts = { up: 0, down: 0, succeeded: 0, failed: 0, outOfService: 0, unknown: 0, starting: 0 };

    serverGroup.instances.forEach((instance) => {
      const tgHealth: IAmazonHealth = instance.health.find((h) => h.type === 'TargetGroup') as IAmazonHealth;
      if (tgHealth) {
        const matchedHealth: ILoadBalancer = tgHealth.targetGroups.find((tg) => tg.name === match.name);

        if (matchedHealth !== undefined && matchedHealth.healthState !== undefined) {
          const healthState = matchedHealth.healthState.toLowerCase();
          if (targetGroup.instanceCounts[healthState] !== undefined) {
            targetGroup.instanceCounts[healthState]++;
          }
        }
      }
    });
    return targetGroup;
  }

  public static populateTargetGroups(
    application: Application,
    serverGroup: IAmazonServerGroup,
  ): PromiseLike<ITargetGroup[]> {
    return $q
      .all([AccountService.getAccountDetails(serverGroup.account), application.getDataSource('loadBalancers').ready()])
      .then((data) => {
        const awsAccount = (data[0] && data[0].awsAccount) || serverGroup.account;
        const loadBalancers: IAmazonApplicationLoadBalancer[] = (application.loadBalancers
          .data as ILoadBalancer[]).filter(
          (lb) => lb.loadBalancerType === 'application' || lb.loadBalancerType === 'network',
        ) as IAmazonApplicationLoadBalancer[];
        const targetGroups = serverGroup.targetGroups
          .map((targetGroupName: string) => {
            const allTargetGroups = flatten(loadBalancers.map((lb) => lb.targetGroups || []));
            const targetGroup = allTargetGroups.find(
              (tg) => tg.name === targetGroupName && tg.region === serverGroup.region && tg.account === awsAccount,
            );
            return this.buildTargetGroup(targetGroup, serverGroup);
          })
          .filter((tg) => tg);
        return targetGroups;
      });
  }
}
