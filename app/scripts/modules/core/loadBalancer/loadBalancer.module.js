import {LOAD_BALANCERS_TAG_COMPONENT} from './loadBalancersTag.component';
import {LOAD_BALANCER_STATES} from './loadBalancer.states';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.loadBalancer', [
    require('./AllLoadBalancersCtrl.js'),
    require('./loadBalancerServerGroup.directive.js'),
    LOAD_BALANCERS_TAG_COMPONENT,
    LOAD_BALANCER_STATES,
    require('./filter/LoadBalancerFilterCtrl.js'),
    require('./loadBalancer.directive.js'),
    require('./loadBalancer/loadBalancer.pod.directive.js'),
    require('./loadBalancer.dataSource')
  ]);
