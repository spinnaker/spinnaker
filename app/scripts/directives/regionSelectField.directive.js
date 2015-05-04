'use strict';


angular.module('deckApp')
  .directive('regionSelectField', function (settings) {
    return {
      restrict: 'E',
      templateUrl: 'views/directives/regionSelectField.html',
      scope: {
        regions: '=',
        component: '=',
        field: '@',
        account: '=',
        provider: '=',
        onChange: '&',
        labelColumns: '@',
        fieldColumns: '@',
        readOnly: '=',
      },
      link: function(scope) {
        function groupRegions(regions) {
          var regionNames = _.pluck(regions, 'name');
          if (regionNames) {
            scope.primaryRegions = regionNames.sort();
          }
          if (regionNames && regionNames.length) {
            scope.primaryRegions = regionNames.filter(function(region) {
              return settings.providers[scope.provider].primaryRegions.indexOf(region) !== -1;
            }).sort();
            scope.secondaryRegions = _.xor(regionNames, scope.primaryRegions).sort();
          }
        }
        scope.dividerText = '---------------';
        scope.$watch('regions', groupRegions);
      }
    };
  }
);
