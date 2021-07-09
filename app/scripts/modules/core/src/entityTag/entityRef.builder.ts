import { Application } from '../application/application.model';
import {
  IEntityRef,
  ILoadBalancer,
  IRegionalCluster,
  ISecurityGroup,
  IServerGroup,
  IServerGroupManager,
} from '../domain';

export class EntityRefBuilder {
  public static buildServerGroupRef(serverGroup: IServerGroup): IEntityRef {
    return {
      cloudProvider: serverGroup.cloudProvider,
      entityType: 'servergroup',
      entityId: serverGroup.name,
      account: serverGroup.account,
      region: serverGroup.region,
    };
  }

  public static buildServerGroupManagerRef(serverGroupManager: IServerGroupManager): IEntityRef {
    return {
      cloudProvider: serverGroupManager.cloudProvider,
      entityType: 'servergroupmanager',
      entityId: serverGroupManager.name,
      account: serverGroupManager.account,
      region: serverGroupManager.region,
    };
  }

  public static buildLoadBalancerRef(loadBalancer: ILoadBalancer): IEntityRef {
    return {
      cloudProvider: loadBalancer.cloudProvider,
      entityType: 'loadbalancer',
      entityId: loadBalancer.name,
      account: loadBalancer.account,
      region: loadBalancer.region,
    };
  }

  public static buildApplicationRef(application: Application): IEntityRef {
    return {
      cloudProvider: '*',
      entityType: 'application',
      entityId: application.name,
    };
  }

  public static buildRegionalClusterRef(cluster: IRegionalCluster): IEntityRef {
    return {
      cloudProvider: cluster.cloudProvider,
      entityType: 'cluster',
      entityId: cluster.name,
      account: cluster.account,
      region: cluster.region,
    };
  }

  public static buildSecurityGroupRef(securityGroup: ISecurityGroup): IEntityRef {
    return {
      cloudProvider: securityGroup.cloudProvider,
      entityType: 'securitygroup',
      entityId: securityGroup.name,
      account: securityGroup.accountName,
      region: securityGroup.region,
      vpcId: securityGroup.vpcId,
    };
  }

  public static getBuilder(type: string): (entity: any) => IEntityRef {
    switch (type) {
      case 'application':
        return this.buildApplicationRef;
      case 'serverGroup':
        return this.buildServerGroupRef;
      case 'serverGroupManager':
        return this.buildServerGroupManagerRef;
      case 'loadBalancer':
        return this.buildLoadBalancerRef;
      case 'cluster':
        return this.buildRegionalClusterRef;
      case 'securityGroup':
        return this.buildSecurityGroupRef;
      default:
        return null;
    }
  }
}
