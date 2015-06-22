'use strict';

/**
 * @ngdoc directive
 * @name spinnaker.directive:insightMenu
 * @description
 * # insightMenu
 */

module.exports = function () {
  return {
    template: require('../modules/insight/insightmenu.html'),
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
