'use strict';

import * as angular from 'angular';

export const GOOGLE_COMMON_FOOTER_DIRECTIVE = 'spinnaker.google.footer.directive';
export const name = GOOGLE_COMMON_FOOTER_DIRECTIVE; // for backwards compatibility
angular.module(GOOGLE_COMMON_FOOTER_DIRECTIVE, []).directive('gceFooter', function () {
  return {
    restrict: 'E',
    templateUrl: require('./footer.directive.html'),
    scope: {},
    bindToController: {
      action: '&',
      isValid: '&',
      cancel: '&',
      account: '=?',
      verification: '=?',
    },
    controllerAs: 'vm',
    controller: angular.noop,
  };
});
