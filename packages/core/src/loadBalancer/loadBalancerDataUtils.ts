import { Application } from '../application';
import { IHealth, ILoadBalancer, IServerGroup } from '../domain';

export class LoadBalancerDataUtils {
  private static buildLoadBalancer(match: ILoadBalancer, serverGroup: IServerGroup): ILoadBalancer {
    if (!match) {
      return null;
    }

    const loadBalancer: ILoadBalancer = { name: match.name, vpcId: match.vpcId, cloudProvider: match.cloudProvider };
    loadBalancer.instanceCounts = { up: 0, down: 0, succeeded: 0, failed: 0, outOfService: 0, unknown: 0, starting: 0 };

    serverGroup.instances.forEach((instance) => {
      const lbHealth: IHealth = instance.health.find((h) => h.type === 'LoadBalancer');
      if (lbHealth) {
        const matchedHealth: ILoadBalancer = lbHealth.loadBalancers.find((lb) => lb.name === match.name);

        if (
          matchedHealth !== undefined &&
          matchedHealth.healthState !== undefined &&
          loadBalancer.instanceCounts[matchedHealth.healthState.toLowerCase()] !== undefined
        ) {
          loadBalancer.instanceCounts[matchedHealth.healthState.toLowerCase()]++;
        }
      }
    });
    return loadBalancer;
  }

  public static populateLoadBalancers(
    application: Application,
    serverGroup: IServerGroup,
  ): PromiseLike<ILoadBalancer[]> {
    return application
      .getDataSource('loadBalancers')
      .ready()
      .then(() => {
        const loadBalancers = serverGroup.loadBalancers.map((lbName: string) => {
          const match = application.getDataSource('loadBalancers').data.find((lb: ILoadBalancer): boolean => {
            return (
              lb.name === lbName &&
              lb.account === serverGroup.account &&
              (lb.region === serverGroup.region || lb.region === 'global')
            );
          });

          return this.buildLoadBalancer(match, serverGroup);
        });

        return loadBalancers.filter((x) => !!x);
      });
  }
}
