'use strict';


var angular = require('angular');

angular.module('deckApp')
  .controller('InfrastructureCtrl', function($scope, infrastructureSearch) {
    var search = infrastructureSearch();
    $scope.$watch('query', function(query) {
      search.query(query).then(function(result) {
        $scope.categories = result;

      });
    });
  });
