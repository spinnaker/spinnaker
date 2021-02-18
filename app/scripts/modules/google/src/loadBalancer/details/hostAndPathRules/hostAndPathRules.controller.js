import { module } from 'angular';

import { GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_SERVICE } from './hostAndPathRules.service';

('use strict');

export const GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_CONTROLLER =
  'spinnaker.deck.gce.loadBalancer.hostAndPathRules.controller';
export const name = GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_CONTROLLER; // for backwards compatibility
module(GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_CONTROLLER, [
  GOOGLE_LOADBALANCER_DETAILS_HOSTANDPATHRULES_HOSTANDPATHRULES_SERVICE,
]).controller('gceHostAndPathRulesCtrl', [
  'hostRules',
  'defaultService',
  'loadBalancerName',
  '$uibModalInstance',
  'hostAndPathRulesService',
  function (hostRules, defaultService, loadBalancerName, $uibModalInstance, hostAndPathRulesService) {
    this.table = hostAndPathRulesService.buildTable(hostRules, defaultService);
    this.loadBalancerName = loadBalancerName;
    this.close = $uibModalInstance.dismiss;
  },
]);
