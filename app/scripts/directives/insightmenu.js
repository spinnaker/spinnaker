'use strict';


/**
 * @ngdoc directive
 * @name spinnaker.directive:insightMenu
 * @description
 * # insightMenu
 */
angular.module('spinnaker')
  .directive('insightMenu', function () {
    return {
      templateUrl: 'scripts/modules/insight/insightmenu.html',
      restrict: 'E',
      replace: true,
      scope: {
        actions: '=',
        title: '@',
        icon: '@',
        rightAlign: '&',
      },
    };
  }
);
