'use strict';

/**
 * @ngdoc directive
 * @name spinnaker.directive:insightMenu
 * @description
 * # insightMenu
 */

module.exports = function () {
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
  };
};
