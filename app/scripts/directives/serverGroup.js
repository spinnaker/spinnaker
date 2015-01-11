'use strict';


angular.module('deckApp')
  .directive('serverGroup', function ($rootScope, $timeout) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'views/application/cluster/serverGroup.html',
      scope: {
        cluster: '=',
        serverGroup: '=',
        displayOptions: '=',
        application: '=',
      },
      link: function (scope, el) {
        // stolen from uiSref directive
        var base = el.parent().inheritedData('$uiView').state;

        scope.$state = $rootScope.$state;

        scope.loadDetails = function(e) {
          $timeout(function() {
            var serverGroup = scope.serverGroup;
            // anything handled by ui-sref or actual links should be ignored
            if (e.isDefaultPrevented() || (e.originalEvent && e.originalEvent.target.href)) {
              return;
            }
            var params = {
              region: serverGroup.region,
              accountId: serverGroup.account,
              serverGroup: serverGroup.name
            };
            // also stolen from uiSref directive
            scope.$state.go('.serverGroup', params, {relative: base, inherit: true});
          });
        };

        if (scope.serverGroup.isDisabled) {
          scope.serverGroup.instances.forEach(function(instance) {
            instance.healthStatus = 'Disabled';
          });
        }
      }
    };
  }
);
