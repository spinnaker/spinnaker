import { module } from 'angular';

export const INSIGHT_MENU_DIRECTIVE = 'spinnaker.core.insightMenu.directive';
module(INSIGHT_MENU_DIRECTIVE, []).directive('insightMenu', function () {
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
    link: function (scope) {
      scope.status = { isOpen: false };
    },
  };
});
