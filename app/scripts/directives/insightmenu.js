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
