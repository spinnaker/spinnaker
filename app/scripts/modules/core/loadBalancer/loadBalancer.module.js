import { INSTANCES_COMPONENT } from 'core/instance/instances.component';
import { LOAD_BALANCERS_TAG_COMPONENT } from './loadBalancersTag.component';
import { LOAD_BALANCER_STATES } from './loadBalancer.states';
import { LOAD_BALANCER_FILTER } from './filter/loadBalancer.filter.component';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.loadBalancer', [
    LOAD_BALANCERS_TAG_COMPONENT,
    LOAD_BALANCER_STATES,
    LOAD_BALANCER_FILTER,
    INSTANCES_COMPONENT,
    require('./loadBalancer.dataSource')
  ]);
