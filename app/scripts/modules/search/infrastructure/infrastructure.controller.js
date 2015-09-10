'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.search.infrastructure.controller', [
  require('./infrastructureSearch.service.js'),
  require('../../core/history/recentHistory.service.js'),
  require('../searchResult/searchResult.directive.js'),
])
  .controller('InfrastructureCtrl', function($scope, infrastructureSearchService, $stateParams, $location, searchService, _, recentHistoryService) {

    var search = infrastructureSearchService();

    $scope.viewState = {
      searching: false,
    };

    $scope.recentItems = ['applications', 'loadBalancers', 'serverGroups', 'instances']
      .map((category) => {
        return {
          category: category,
          results: recentHistoryService.getItems(category).map((result) => {
            let routeParams = angular.extend(result.params, result.extraData);
            result.name = search.formatRouteResult(category, routeParams);
            return result;
          })
        };
      })
      .filter((category) => { return category.results.length; });

    this.hasRecentItems = $scope.recentItems.some((category) => { return category.results.length > 0; });

    $scope.pageSize = searchService.defaultPageSize;

    if (angular.isDefined($stateParams.q)) {
      $scope.query = $stateParams.q;
    }
    $scope.$watch('query', function(query) {
      $scope.viewState.searching = true;
      $scope.categories = null;
      search.query(query).then(function(result) {
        $scope.categories = result;
        $scope.moreResults = _.sum(result, function(resultSet) {
          return resultSet.results.length;
        }) === $scope.pageSize;
        $location.search('q', query);
        $scope.viewState.searching = false;
      });
    });

    this.hasResults = function() {
      return angular.isObject($scope.categories) && Object.keys($scope.categories).length > 0 && $scope.query.length > 0;
    };

    this.noMatches = function() {
      return angular.isObject($scope.categories) && Object.keys($scope.categories).length === 0 && $scope.query && $scope.query.length > 0;
    };
  }).name;
