'use strict';

/**
 * @ngdoc directive
 * @name scumApp.directive:insightMenu
 * @description
 * # insightMenu
 */
angular.module('scumApp')
  .directive('insightMenu', function () {
    return {
      templateUrl: 'views/insightmenu.html',
      restrict: 'E',
      replace: true,
      scope: {
        actions: '=',
        title: '@',
        icon: '@',
      },
    };
  });
