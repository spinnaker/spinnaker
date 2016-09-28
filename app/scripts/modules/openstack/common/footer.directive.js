'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.openstack.footer.directive', [
  ])
  .directive('openstackFooter', function () {
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
