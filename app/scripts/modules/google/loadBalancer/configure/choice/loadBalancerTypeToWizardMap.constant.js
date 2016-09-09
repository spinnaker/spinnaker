'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.loadBalancerTypeToWizard.constant', [])
  .constant('loadBalancerTypeToWizardMap', {
    'Network': {
      createTemplateUrl: require('../createLoadBalancer.html'),
      editTemplateUrl: require('../editLoadBalancer.html'),
      controller: 'gceCreateLoadBalancerCtrl'
    },
    'HTTP(S)': {
      createTemplateUrl: require('../http/createHttpLoadBalancer.html'),
      editTemplateUrl: require('../http/editHttpLoadBalancer.html'),
      controller: 'gceCreateHttpLoadBalancerCtrl'
    }
  });
