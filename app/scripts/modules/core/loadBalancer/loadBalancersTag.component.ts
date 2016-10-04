import { Application } from '../application/application.model.ts';
import {
  LoadBalancer,
  ServerGroup,
  Health
} from '../domain';
import {InstanceCounts} from "../domain/instanceCounts";

export class LoadBalancersTagController implements ng.IComponentController {

  public application: Application;
  public serverGroup: ServerGroup;
  public loadBalancers: LoadBalancer[];

  public $onInit() {
    this.application.getDataSource('loadBalancers').ready().then(() => {
      let serverGroup: ServerGroup = this.serverGroup;
      this.loadBalancers = serverGroup.loadBalancers.map( (lbName: string) => {
        let match = this.application.getDataSource('loadBalancers')
          .data
          .find((lb: LoadBalancer): boolean => {
            return lb.name === lbName
              && lb.account === serverGroup.account
              && lb.region === serverGroup.region
          });

        return this.buildLoadBalancer(match);
      });
    });
  }

  private buildLoadBalancer(match: any) {
    if (!match) {
      return null;
    }

    let loadBalancer: LoadBalancer = new LoadBalancer(match.name, match.vpcId);
    loadBalancer.instanceCounts = <InstanceCounts>{up:0, down: 0, succeeded: 0, failed: 0, unknown: 0};

    this.serverGroup.instances.forEach(instance => {
      let lbHealth: Health = instance.health.find(h => h.type === 'LoadBalancer');
      if (lbHealth) {

        let matchedHealth: LoadBalancer = lbHealth.loadBalancers.find(lb => lb.name === match.name);

        if ( matchedHealth && loadBalancer.instanceCounts[matchedHealth.healthState.toLowerCase()] !== undefined) {
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

  public controller: ng.IComponentController = LoadBalancersTagController;
  public templateUrl: string = require('./loadBalancer/loadBalancersTag.html');

}

const moduleName = 'spinnaker.core.loadBalancer.tag.directive';

angular.module(moduleName, [])
  .component('loadBalancersTag', new LoadBalancersTagComponent());

export default moduleName;
