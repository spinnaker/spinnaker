'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.azure.footer.directive', []).directive('azureFooter', function() {
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
