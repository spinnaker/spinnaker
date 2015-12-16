'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.insightMenu.directive', [])
  .directive('insightMenu', function () {
    return {
      templateUrl: require('./insightmenu.directive.html'),
      restrict: 'E',
      replace: true,
      scope: {
        actions: '=',
        title: '@',
        icon: '@',
        rightAlign: '&',
      },
      link: function(scope) {
        scope.status = {isOpen: false};
      }
    };
  });
