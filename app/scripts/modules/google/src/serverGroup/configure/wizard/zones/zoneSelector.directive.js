'use strict';

import * as angular from 'angular';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_ZONES_ZONESELECTOR_DIRECTIVE =
  'spinnaker.google.serverGroup.configure.wizard.capacity.zone.directive';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_ZONES_ZONESELECTOR_DIRECTIVE; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_ZONES_ZONESELECTOR_DIRECTIVE, [])
  .directive('gceZoneSelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./zoneSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
