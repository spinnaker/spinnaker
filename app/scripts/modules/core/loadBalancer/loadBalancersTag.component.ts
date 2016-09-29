import {Application} from '../application/application.model.ts';

export class LoadBalancersTagController implements ng.IComponentController {

  public application: Application;
  public serverGroup: any;
  public loadBalancers: any[];

  public $onInit() {
    this.application.getDataSource('loadBalancers').ready().then(() => {
      let serverGroup = this.serverGroup;
      this.loadBalancers = serverGroup.loadBalancers.map(lbName => {
        let [match] = this.application.getDataSource('loadBalancers').data
          .filter(lb => lb.name === lbName && lb.account === serverGroup.account && lb.region === serverGroup.region);
        return this.buildLoadBalancer(match);
      });
    });
  }

  private buildLoadBalancer(match) {
    if (!match) {
      return null;
    }
    let loadBalancer = {
      name: match.name,
      vpcId: match.vpcId,
      instanceCounts: {
        up: 0,
        down: 0,
        succeeded: 0,
        failed: 0,
        unknown: 0
      }
    };
    this.serverGroup.instances.forEach(instance => {
      let [lbHealth] = instance.health.filter(h => h.type === 'LoadBalancer');
      if (lbHealth) {
        let [matchedHealth] = lbHealth.loadBalancers.filter(lb => lb.name === match.name);
        if (matchedHealth && loadBalancer.instanceCounts[matchedHealth.healthState.toLowerCase()] !== undefined) {
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
