'use strict';

var angular = require('angular');

angular.module('deckApp')
  .controller('GlobalSearchCtrl', function($scope, $element, infrastructureSearch, urlBuilder) {
    $scope.$watch('query', function(query) {
      var displayNameLookup = {
        serverGroups: 'serverGroup',
        serverGroupInstances: 'instanceId',
        clusters: 'cluster',
        applications: 'application',
      };

      var categoryNameLookup = {
        serverGroups: 'Server Groups',
        serverGroupInstances: 'Instances',
        clusters: 'Clusters',
        applications: 'Applications',
      };

      infrastructureSearch.query(query).subscribe(function(result) {
        var tmp = result.data[0].results.reduce(function(categories, entry) {
          var cat = entry.type;
          entry.name = entry[displayNameLookup[entry.type]];
          entry.href = urlBuilder(entry);
          if (angular.isDefined(categories[cat])) {
            categories[cat].push(entry);
          } else {
            categories[cat] = [entry];
          }
          return categories;
        }, {});
        $scope.categories = Object.keys(tmp).map(function(cat) {
          return {
            category: categoryNameLookup[cat],
            results: tmp[cat],
          };
        });
        this.dispose();
      });
    });

    $scope.showSearchResults = false;

    this.showSearchResults = function() {
      $scope.showSearchResults = true;
    };
  });
