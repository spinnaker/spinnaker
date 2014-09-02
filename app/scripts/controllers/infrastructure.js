'use strict';


var angular = require('angular');

angular.module('deckApp')
  .controller('InfrastructureCtrl', function($scope, infrastructureSearch, $stateParams, $location) {
    var search = infrastructureSearch();
    if (angular.isDefined($stateParams.q)) {
      $scope.query = $stateParams.q;
    }
    $scope.$watch('query', function(query) {
      search.query(query).then(function(result) {
        $scope.categories = result;
        $location.search('q', query);
      });
    });
  });
