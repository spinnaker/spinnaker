'use strict';

import { FirewallLabels } from '@spinnaker/core';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.oracle.securityGroup.create.controller', [])
  .controller('oracleCreateSecurityGroupCtrl', [
    '$scope',
    '$uibModalInstance',
    function($scope, $uibModalInstance) {
      this.cancel = () => {
        $uibModalInstance.dismiss();
      };
      $scope.firewallLabel = FirewallLabels.get('Firewall');
    },
  ]);
