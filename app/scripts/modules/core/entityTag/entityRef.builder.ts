import {ServerGroup} from '../domain/serverGroup';
import {ILoadBalancer} from '../domain/loadBalancer';
import {Application} from '../application/application.model';
import {IEntityRef} from '../domain/IEntityTags';
import {ISecurityGroup} from '../domain/ISecurityGroup';
import {IRegionalCluster} from '../domain/IRegionalCluster';

export class EntityRefBuilder {

  public static buildServerGroupRef(serverGroup: ServerGroup): IEntityRef {
    return {
      cloudProvider: serverGroup.cloudProvider,
      entityType: 'servergroup',
      entityId: serverGroup.name,
      account: serverGroup.account,
      region: serverGroup.region,
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
