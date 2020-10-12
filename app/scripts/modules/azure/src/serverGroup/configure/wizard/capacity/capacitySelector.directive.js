'use strict';

import { module } from 'angular';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_CAPACITYSELECTOR_DIRECTIVE =
  'spinnaker.azure.serverGroup.configure.wizard.capacity.selector.directive';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_CAPACITYSELECTOR_DIRECTIVE; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_CAPACITYSELECTOR_DIRECTIVE, [])
  .directive('azureServerGroupCapacitySelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./capacitySelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'cap',
      controller: 'azureServerGroupCapacitySelectorCtrl',
    };
  })
  .controller('azureServerGroupCapacitySelectorCtrl', function () {});
