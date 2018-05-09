'use strict';

import { FirewallLabels } from 'root/app/scripts/modules/core/src';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.oraclebmcs.securityGroup.create.controller', [])
  .controller('oraclebmcsCreateSecurityGroupCtrl', function($scope, $uibModalInstance) {
    this.cancel = () => {
      $uibModalInstance.dismiss();
    };
    $scope.firewallLabel = FirewallLabels.get('Firewall');
  });
