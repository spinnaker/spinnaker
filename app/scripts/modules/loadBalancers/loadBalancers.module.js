'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.loadBalancer', [
    require('./AllLoadBalancersCtrl.js'),
    require('./loadBalancerServerGroup.directive.js'),
    require('./loadBalancersTag.directive.js'),
    require('./details/aws/LoadBalancerDetailsCtrl.js'),
    require('./details/gce/LoadBalancerDetailsCtrl.js'),
    require('./configure/aws/CreateLoadBalancerCtrl.js'),
    require('./configure/gce/CreateLoadBalancerCtrl.js'),
    require('./filter/LoadBalancerFilterCtrl.js'),
    require('./loadBalancer.directive.js'),
    require('./loadBalancer/loadBalancer.pod.directive.js'),
  ]).name;
