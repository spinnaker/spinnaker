'use strict';


angular.module('deckApp')
  .directive('serverGroup', function ($rootScope, $timeout) {
    return {
      restrict: 'E',
      replace: true,
      templateUrl: 'scripts/modules/cluster/serverGroup.html',
      scope: {
        cluster: '=',
        serverGroup: '=',
        displayOptions: '=',
        sortFilter: '=',
        application: '=',
        parentHeading: '=',
      },
      link: function (scope, el) {
        // stolen from uiSref directive
        var base = el.parent().inheritedData('$uiView').state;

        scope.$state = $rootScope.$state;

        scope.loadDetails = function(e) {
          $timeout(function() {
            var serverGroup = scope.serverGroup;
            // anything handled by ui-sref or actual links should be ignored
            if (e.isDefaultPrevented() || (e.originalEvent && (e.originalEvent.defaultPrevented || e.originalEvent.target.href))) {
              return;
            }
            var params = {
              region: serverGroup.region,
              accountId: serverGroup.account,
              serverGroup: serverGroup.name,
              provider: serverGroup.type,
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

        scope.headerIsSticky = function() {
          if (!scope.displayOptions.showInstances) {
            return false;
          }
          return scope.serverGroup.instances.length;
        };
      }
    };
  }
);
