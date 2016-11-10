import {module} from 'angular';

export interface IComponentName {
  application: string;
  stack: string;
  freeFormDetails: string;
}

export class NamingService {
  private VERSION_PATTERN: RegExp = /(v\d{3})/;

  public parseServerGroupName(serverGroupName: string): IComponentName {
    const result: IComponentName = {
      application: '',
      stack: '',
      freeFormDetails: ''
    };

    if (!serverGroupName) {
      return result;
    }
    const split: string[] = serverGroupName.split('-'),
          isVersioned = this.VERSION_PATTERN.test(split[split.length - 1]);

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

    return result;
  }

  public getClusterName(app: string, stack: string, detail: string): string {
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

  public getSequence(serverGroupName: string): string {
    const split = serverGroupName.split('-'),
      isVersioned = this.VERSION_PATTERN.test(split[split.length - 1]);

    if (isVersioned) {
      return split.pop();
    }
    return null;
  }

  public parseLoadBalancerName(loadBalancerName: string): IComponentName {
    const split = loadBalancerName.split('-'),
      result: IComponentName = {
        application: split[0],
        stack: '',
        freeFormDetails: ''
      };

    if (split.length > 1) {
      result.stack = split[1];
    }
    if (split.length > 2) {
      result.freeFormDetails = split.slice(2, split.length).join('-');
    }
    return result;
  }

  public parseSecurityGroupName(securityGroupName: string): IComponentName {
    return this.parseLoadBalancerName(securityGroupName);
  }
}

export const NAMING_SERVICE = 'spinnaker.core.naming.service';

module(NAMING_SERVICE, [])
  .service('namingService', NamingService);
