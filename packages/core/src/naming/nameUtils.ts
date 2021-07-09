import { isNil, padStart, pickBy } from 'lodash';

import { IMoniker } from './IMoniker';
import { Application } from '../application';
import { ILoadBalancer, IServerGroup } from '../domain';

export interface IComponentName {
  application: string;
  stack: string;
  freeFormDetails: string;
  cluster: string;
}

export class NameUtils {
  public static VERSION_PATTERN = /(v\d{3})/;

  public static parseServerGroupName(serverGroupName: string): IComponentName {
    const result: IComponentName = {
      application: '',
      stack: '',
      freeFormDetails: '',
      cluster: '',
    };

    if (!serverGroupName) {
      return result;
    }
    const split: string[] = serverGroupName.split('-');
    const isVersioned = NameUtils.VERSION_PATTERN.test(split[split.length - 1]);

    result.application = split[0];

    if (isVersioned) {
      split.pop();
    }

    if (split.length > 1) {
      result.stack = split[1];
    }
    if (split.length > 2) {
      result.freeFormDetails = split.slice(2, split.length).join('-');
    }
    result.cluster = NameUtils.getClusterNameFromServerGroupName(serverGroupName);

    return result;
  }

  public static parseClusterName(clusterName: string): IComponentName {
    return NameUtils.parseServerGroupName(clusterName);
  }

  public static getClusterName(app: string, stack: string, detail: string): string {
    let clusterName = app;
    if (stack) {
      clusterName += `-${stack}`;
    }
    if (!stack && detail) {
      clusterName += `-`;
    }
    if (detail) {
      clusterName += `-${detail}`;
    }
    return clusterName;
  }

  public static getClusterNameFromServerGroupName(serverGroupName: string): string {
    const split = serverGroupName.split('-');
    const isVersioned = NameUtils.VERSION_PATTERN.test(split[split.length - 1]);

    if (isVersioned) {
      split.pop();
    }
    return split.join('-');
  }

  public static getSequence(monikerSequence: number): string {
    if (isNil(monikerSequence)) {
      return null;
    } else {
      return `v${padStart(monikerSequence.toString(), 3, '0')}`;
    }
  }

  public static parseLoadBalancerName(loadBalancerName: string): IComponentName {
    const split = loadBalancerName.split('-');
    const result: IComponentName = {
      application: split[0],
      stack: '',
      freeFormDetails: '',
      cluster: '',
    };

    if (split.length > 1) {
      result.stack = split[1];
    }
    if (split.length > 2) {
      result.freeFormDetails = split.slice(2, split.length).join('-');
    }
    result.cluster = NameUtils.getClusterName(result.application, result.stack, result.freeFormDetails);
    return result;
  }

  public static parseSecurityGroupName(securityGroupName: string): IComponentName {
    return NameUtils.parseLoadBalancerName(securityGroupName);
  }

  public static getMoniker(app: string, stack: string, detail: string): IMoniker {
    return pickBy({ app, stack, detail });
  }

  public static getMonikerForInstance(
    cloudProvider: string,
    instanceId: string,
    app: Application,
  ): PromiseLike<IMoniker> {
    return app.ready().then(() => {
      const serverGroups = app.getDataSource('serverGroups').data as IServerGroup[];
      const loadBalancers = app.getDataSource('loadBalancers').data as ILoadBalancer[];
      const loadBalancerServerGroups = loadBalancers
        .map((lb) => lb.serverGroups)
        .reduce((acc, sg) => acc.concat(sg), []);

      const hasInstance = (obj: IServerGroup | ILoadBalancer) => {
        return (
          obj.cloudProvider === cloudProvider && (obj.instances || []).some((instance) => instance.id === instanceId)
        );
      };

      const all: Array<IServerGroup | ILoadBalancer> = []
        .concat(serverGroups)
        .concat(loadBalancers)
        .concat(loadBalancerServerGroups);
      const found = all.find(hasInstance);
      return found && found.moniker;
    });
  }
}
