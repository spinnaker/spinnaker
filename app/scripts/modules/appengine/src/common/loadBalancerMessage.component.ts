import { module } from 'angular';

const appengineLoadBalancerMessageComponent: ng.IComponentOptions = {
  bindings: { showCreateMessage: '<', columnOffset: '@', columns: '@' },
  templateUrl: require('./loadBalancerMessage.component.html'),
};

export const APPENGINE_LOAD_BALANCER_CREATE_MESSAGE = 'spinnaker.appengine.loadBalancer.createMessage.component';

module(APPENGINE_LOAD_BALANCER_CREATE_MESSAGE, []).component(
  'appengineLoadBalancerMessage',
  appengineLoadBalancerMessageComponent,
);
