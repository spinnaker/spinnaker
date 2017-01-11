import {module} from 'angular';
import { Application } from '../application/application.model';
import {
  ILoadBalancer,
  ServerGroup,
  Health
} from '../domain';
import {InstanceCounts} from '../domain/instanceCounts';

export class LoadBalancersTagController implements ng.IComponentController {

  public application: Application;
  public serverGroup: ServerGroup;
  public loadBalancers: ILoadBalancer[];

  public $onInit() {
    this.application.getDataSource('loadBalancers').ready().then(() => {
      let serverGroup: ServerGroup = this.serverGroup;
      this.loadBalancers = serverGroup.loadBalancers.map( (lbName: string) => {
        let match = this.application.getDataSource('loadBalancers')
          .data
          .find((lb: ILoadBalancer): boolean => {
            return lb.name === lbName
              && lb.account === serverGroup.account
              && (lb.region === serverGroup.region || lb.region === 'global');
          });

        return this.buildLoadBalancer(match);
      });
    });
  }

  private buildLoadBalancer(match: any) {
    if (!match) {
      return null;
    }

    let loadBalancer: ILoadBalancer = { name: match.name, vpcId: match.vpcId, cloudProvider: match.cloudProvider };
    loadBalancer.instanceCounts = <InstanceCounts>{up: 0, down: 0, succeeded: 0, failed: 0, unknown: 0};

    this.serverGroup.instances.forEach(instance => {
      let lbHealth: Health = instance.health.find(h => h.type === 'LoadBalancer');
      if (lbHealth) {

        let matchedHealth: ILoadBalancer = lbHealth.loadBalancers.find(lb => lb.name === match.name);

        if (matchedHealth !== undefined && matchedHealth.healthState !== undefined && loadBalancer.instanceCounts[matchedHealth.healthState.toLowerCase()] !== undefined) {
          loadBalancer.instanceCounts[matchedHealth.healthState.toLowerCase()]++;
        }
      }
    });
    return loadBalancer;
  };
}

class LoadBalancersTagComponent implements ng.IComponentOptions {
  public bindings: any = {
    application: '=',
    serverGroup: '='
  };

  public controller: any = LoadBalancersTagController;
  public templateUrl: string = require('./loadBalancer/loadBalancersTag.html');

}

export const LOAD_BALANCERS_TAG_COMPONENT = 'spinnaker.core.loadBalancer.tag.directive';
module(LOAD_BALANCERS_TAG_COMPONENT, [])
  .component('loadBalancersTag', new LoadBalancersTagComponent());
