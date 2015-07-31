'use strict';

/**
 * @ngdoc directive
 * @name spinnaker.directive:insightMenu
 * @description
 * # insightMenu
 */

require('../modules/insight/insightmenu.html');

module.exports = function () {
  return {
    templateUrl: require('../modules/insight/insightmenu.html'),
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
