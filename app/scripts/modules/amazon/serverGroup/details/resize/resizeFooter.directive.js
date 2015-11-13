'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.amazon.serverGroup.details.resize.footer.directive', [
  ])
  .directive('awsResizeFooter', function () {
    return {
      restrict: 'E',
      templateUrl: require('./resizeFooter.directive.html'),
      scope: {},
      bindToController: {
        resize: '&',
        isValid: '&',
        cancel: '&',
      },
      controllerAs: 'vm',
      controller: angular.noop,
    };
  })
  .name;
