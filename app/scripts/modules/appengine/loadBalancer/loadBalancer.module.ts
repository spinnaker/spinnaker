import {APPENGINE_LOAD_BALANCER_CREATE_MESSAGE} from './configure/wizard/createLoadBalancerMessage.component';
import {APPENGINE_LOAD_BALANCER_DETAILS_CTRL} from './details/details.controller';
import {APPENGINE_LOAD_BALANCER_TRANSFORMER} from './transformer';
import {APPENGINE_LOAD_BALANCER_WIZARD_CTRL} from './configure/wizard/wizard.controller';

export const APPENGINE_LOAD_BALANCER_MODULE = 'spinnaker.appengine.loadBalancer.module';

angular.module(APPENGINE_LOAD_BALANCER_MODULE, [
  APPENGINE_LOAD_BALANCER_CREATE_MESSAGE,
  APPENGINE_LOAD_BALANCER_TRANSFORMER,
  APPENGINE_LOAD_BALANCER_WIZARD_CTRL,
  APPENGINE_LOAD_BALANCER_DETAILS_CTRL,
]);
