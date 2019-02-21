'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.deck.gce.loadBalancer.hostAndPathRules.controller', [require('./hostAndPathRules.service').name])
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
