'use strict';


var angular = require('angular');

angular.module('deckApp')
  .controller('InfrastructureCtrl', function($scope, infrastructureSearch) {
    $scope.$watch('query', function(query) {
      var displayName = {
        serverGroups: 'serverGroup',
        serverGroupInstances: 'instanceId',
        clusters: 'cluster',
      };

      infrastructureSearch.query(query).subscribe(function(result) {
        var tmp = result.data.matches.reduce(function(categories, entry) {
          var cat = entry.type;
          entry.name = entry[displayName[entry.type]];
          if (angular.isDefined(categories[cat])) {
            categories[cat].push(entry);
          } else {
            categories[cat] = [entry];
          }
          return categories;
        }, {});
        $scope.categories = Object.keys(tmp).map(function(cat) {
          return {
            category: cat,
            results: tmp[cat],
          };
        });
        this.dispose();
      });
    });
  });
