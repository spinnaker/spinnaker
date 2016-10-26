'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.gce.loadBalancerTypeToWizard.constant', [])
  .constant('loadBalancerTypeToWizardMap', {
    'NETWORK': {
      label: 'Network',
      createTemplateUrl: require('../network/createLoadBalancer.html'),
      editTemplateUrl: require('../network/editLoadBalancer.html'),
      controller: 'gceCreateLoadBalancerCtrl'
    },
    'HTTP': {
      label: 'HTTP(S)',
      createTemplateUrl: require('../http/createHttpLoadBalancer.html'),
      editTemplateUrl: require('../http/editHttpLoadBalancer.html'),
      controller: 'gceCreateHttpLoadBalancerCtrl'
    },
    'INTERNAL': {
      label: 'Internal',
      createTemplateUrl: require('../internal/createInternalLoadBalancer.html'),
      editTemplateUrl: require('../internal/editInternalLoadBalancer.html'),
      controller: 'gceInternalLoadBalancerCtrl'
    }
  });
