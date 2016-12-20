import {ServerGroup} from '../domain/serverGroup';
import {LoadBalancer} from '../domain/loadBalancer';
import {Application} from '../application/application.model';
import {IEntityRef} from '../domain/IEntityTags';

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

  public static buildLoadBalancerRef(loadBalancer: LoadBalancer): IEntityRef {
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

  public static getBuilder(type: string): (entity: any) => IEntityRef {
    switch (type) {
      case 'application':
        return this.buildApplicationRef;
      case 'serverGroup':
        return this.buildServerGroupRef;
      case 'loadBalancer':
        return this.buildLoadBalancerRef;
      default:
        return null;
    }
  }
}
