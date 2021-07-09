'use strict';

import { module } from 'angular';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_SIMPLECAPACITYSELECTOR_COMPONENT =
  'spinnaker.google.serverGroup.configure.wizard.simpleCapacity.selector.component';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_SIMPLECAPACITYSELECTOR_COMPONENT; // for backwards compatibility
module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_CAPACITY_SIMPLECAPACITYSELECTOR_COMPONENT, [])
  .component('gceServerGroupSimpleCapacitySelector', {
    templateUrl: require('./simpleCapacitySelector.component.html'),
    bindings: {
      command: '=',
      setSimpleCapacity: '=',
    },
    controller: 'gceServerGroupSimpleCapacitySelectorCtrl',
  })
  .controller('gceServerGroupSimpleCapacitySelectorCtrl', function () {
    this.setMinMax = (newVal) => {
      this.command.capacity.min = newVal;
      this.command.capacity.max = newVal;
    };
  });
