'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.search.infrastructure.controller', [
  require('./infrastructureSearch.service.js')
])
  .controller('InfrastructureCtrl', function($scope, infrastructureSearchService, $stateParams, $location, searchService, _) {

    var search = infrastructureSearchService();

    $scope.pageSize = searchService.defaultPageSize;

    if (angular.isDefined($stateParams.q)) {
      $scope.query = $stateParams.q;
    }
    $scope.$watch('query', function(query) {
      search.query(query).then(function(result) {
        $scope.categories = result;
        $scope.moreResults = _.sum(result, function(resultSet) {
          return resultSet.results.length;
        }) === $scope.pageSize;
        $location.search('q', query);
      });
    });

    this.hasResults = function() {
      return angular.isObject($scope.categories) && Object.keys($scope.categories).length > 0 && $scope.query.length > 0;
    };

    this.noMatches = function() {
      return angular.isObject($scope.categories) && Object.keys($scope.categories).length === 0 && $scope.query && $scope.query.length > 0;
    };
  }).name;
