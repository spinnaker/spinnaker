'use strict';

import * as angular from 'angular';

export const ORACLE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_CAPACITYSELECTOR_COMPONENT =
  'spinnaker.oracle.serverGroup.configure.wizard.capacity.selector.component';
export const name = ORACLE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_CAPACITYSELECTOR_COMPONENT; // for backwards compatibility
angular
  .module(ORACLE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_CAPACITYSELECTOR_COMPONENT, [])
  .component('oracleServerGroupCapacitySelector', {
    templateUrl: require('./capacitySelector.component.html'),
    bindings: {
      command: '=',
    },
    controllerAs: 'vm',
    controller: angular.noop,
  });
