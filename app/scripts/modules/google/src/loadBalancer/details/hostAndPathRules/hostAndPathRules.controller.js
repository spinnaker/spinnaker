'use strict';

const angular = require('angular');

export const GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_CONTROLLER =
  'spinnaker.deck.gce.loadBalancer.hostAndPathRules.controller';
export const name = GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_CONTROLLER; // for backwards compatibility
angular
  .module(GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_CONTROLLER, [
    require('./hostAndPathRules.service').name,
  ])
  .controller('gceHostAndPathRulesCtrl', [
    'hostRules',
    'defaultService',
    'loadBalancerName',
    '$uibModalInstance',
    'hostAndPathRulesService',
    function(hostRules, defaultService, loadBalancerName, $uibModalInstance, hostAndPathRulesService) {
      this.table = hostAndPathRulesService.buildTable(hostRules, defaultService);
      this.loadBalancerName = loadBalancerName;
      this.close = $uibModalInstance.dismiss;
    },
  ]);
