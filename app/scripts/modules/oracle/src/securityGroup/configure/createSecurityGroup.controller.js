'use strict';

import { FirewallLabels } from '@spinnaker/core';

const angular = require('angular');

export const ORACLE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUP_CONTROLLER =
  'spinnaker.oracle.securityGroup.create.controller';
export const name = ORACLE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUP_CONTROLLER; // for backwards compatibility
angular
  .module(ORACLE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUP_CONTROLLER, [])
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
