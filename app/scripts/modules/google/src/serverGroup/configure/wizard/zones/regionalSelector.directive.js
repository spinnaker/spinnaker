'use strict';

import * as angular from 'angular';

export const GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_ZONES_REGIONALSELECTOR_DIRECTIVE =
  'spinnaker.google.serverGroup.configure.wizard.capacity.regional.directive';
export const name = GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_ZONES_REGIONALSELECTOR_DIRECTIVE; // for backwards compatibility
angular
  .module(GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_ZONES_REGIONALSELECTOR_DIRECTIVE, [])
  .directive('gceRegionalSelector', function () {
    return {
      restrict: 'E',
      templateUrl: require('./regionalSelector.directive.html'),
      scope: {},
      bindToController: {
        command: '=',
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  });
