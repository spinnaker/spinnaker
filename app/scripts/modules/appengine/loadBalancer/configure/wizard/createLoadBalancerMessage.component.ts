import {module} from 'angular';

class AppEngineLoadBalancerCreateMessageComponent implements ng.IComponentOptions {
  templateUrl: string = require('./createLoadBalancerMessage.component.html');
}

export const APPENGINE_LOAD_BALANCER_CREATE_MESSAGE = 'spinnaker.appengine.loadBalancer.createMessage.component';

module(APPENGINE_LOAD_BALANCER_CREATE_MESSAGE, [])
  .component('appengineLoadBalancerCreateMessage', new AppEngineLoadBalancerCreateMessageComponent());
