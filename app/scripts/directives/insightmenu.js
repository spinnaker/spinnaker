'use strict';


/**
 * @ngdoc directive
 * @name deckApp.directive:insightMenu
 * @description
 * # insightMenu
 */
angular.module('deckApp')
  .directive('insightMenu', function () {
    return {
      templateUrl: 'views/insightmenu.html',
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
