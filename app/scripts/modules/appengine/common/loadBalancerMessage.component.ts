import {module} from 'angular';

class AppengineLoadBalancerMessageComponent implements ng.IComponentOptions {
  public bindings: any = {showCreateMessage: '<', columnOffset: '@', columns: '@'};
  public templateUrl: string = require('./loadBalancerMessage.component.html');
}

export const APPENGINE_LOAD_BALANCER_CREATE_MESSAGE = 'spinnaker.appengine.loadBalancer.createMessage.component';

module(APPENGINE_LOAD_BALANCER_CREATE_MESSAGE, [])
  .component('appengineLoadBalancerMessage', new AppengineLoadBalancerMessageComponent());
