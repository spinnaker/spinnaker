'use strict';

//BEN_TODO

module.exports = function (settings, _) {
  return {
    restrict: 'E',
    templateUrl: require('../../views/directives/regionSelectField.html'),
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
};
