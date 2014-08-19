'use strict';

var angular = require('angular');

angular.module('deckApp')
  .controller('GlobalSearchCtrl', function($scope, $element, infrastructureSearch) {
    var search = infrastructureSearch();
    $scope.$watch('query', function(query) {
      search.query(query).then(function(result) {
        $scope.categories = result;

      });
    });

    $scope.showSearchResults = false;

    this.showSearchResults = function() {
      $scope.showSearchResults = true;
    };
  });
