'use strict';

/**
 * @ngdoc directive
 * @name scumApp.directive:insightMenu
 * @description
 * # insightMenu
 */
module.exports = function() {
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
};
