'use strict';

/* eslint consistent-return:0 */

let angular = require('angular');

module.exports = angular.module('spinnaker.core.search.global.controller', [
  require('../../utils/jQuery.js'),
  require('../searchResult/searchResult.directive.js'),
  require('../searchRank.filter.js'),
  require('../../history/recentHistory.service.js'),
])
  .controller('GlobalSearchCtrl', function($scope, $element, infrastructureSearchService, recentHistoryService,
                                           $stateParams, _, $, clusterFilterService) {
    var ctrl = this;
    var search = infrastructureSearchService();

    $scope.showSearchResults = false;

    function reset() {
      $scope.querying = false;
      $scope.query = null;
      $scope.categories = null;
      $scope.showSearchResults = false;
      $scope.showRecentItems = false;
      ctrl.focussedResult = null;
      $element.find('input').focus();
    }

    this.displayResults = () => {
      if ($scope.query) {
        $scope.showSearchResults = true;
        $scope.showRecentItems = false;
      } else {
        this.showRecentHistory();
      }
    };

    this.hideResults = () => {
      $scope.showSearchResults = false;
      $scope.showRecentItems = false;
    };

    this.showRecentHistory = () => {
      $scope.recentItems = ['applications', 'projects']
        .map((category) => {
          return {
            category: category,
            results: recentHistoryService.getItems(category)
              .map((result) => {
                let routeParams = angular.extend(result.params, result.extraData);
                search.formatRouteResult(category, routeParams, true).then((name) => result.displayName = name);
                return result;
              })
          };
        })
        .filter((category) => {
          return category.results.length;
        });

      $scope.hasRecentItems = $scope.recentItems.some((category) => {
        return category.results.length > 0;
      });
      $scope.showRecentItems = $scope.hasRecentItems;
    };

    this.dispatchQueryInput = function(event) {
      if ($scope.showSearchResults || $scope.hasRecentItems) {
        var code = event.which;
        if (code === 27) { // escape
          return reset();
        }
        if (code === 40) { // down
          return ctrl.focusFirstSearchResult(event);
        }
        if (code === 38) { // up
          return ctrl.focusLastSearchResult(event);
        }
        if (code === 9) { // tab
          if (!event.shiftKey) {
            ctrl.focusFirstSearchResult(event);
          }
          return;
        }
        if (code < 46 && code !== 8) { // bunch of control keys, except delete (46), and backspace (8)
          return;
        }
        if (code === 91 || code === 92 || code === 93) { // left + right command/window, select
          return;
        }
        if (code > 111 && code < 186) { // f keys, misc
          return;
        }
      }
      ctrl.executeQuery();
    };

    this.executeQuery = _.debounce(function() {
      $scope.querying = true;
      search.query($scope.query).then(function (result) {
        $scope.$eval(function () {
          $scope.querying = false;
          $scope.categories = result.filter((category) => category.results.length);
          $scope.showSearchResults = !!$scope.query;
          $scope.showRecentItems = !$scope.query;
        });
      });
    }, 200);

    ctrl.clearFilters = clusterFilterService.overrideFiltersForUrl;

    this.focusFirstSearchResult = function focusFirstSearchResult(event) {
      try {
        event.preventDefault();
        $element.find('ul.dropdown-menu').find('a').first().focus();
      } catch (e) {
        console.log(e);
      }
    };

    this.focusLastSearchResult = function focusLastSearchResult(event) {
      try {
        event.preventDefault();
        $element.find('ul.dropdown-menu').find('a').last().focus();
      } catch (e) {
        console.log(e);
      }
    };

    this.showSearchResults = function() {
      if (angular.isObject($scope.categories)) {
        $scope.showSearchResults = true;
      }
    };

    this.navigateResults = function(event) {
      var $target = $(event.target);
      if (event.which === 27) { // escape
        reset();
      }
      if (event.which === 40) { // down
        ctrl.focussedResult = null;
        try {
          $target
            .parent()
            .nextAll('li.result')[0]
            .children[0]
            .focus();
        } catch (e) {
          ctrl.focusFirstSearchResult(event);
        }
        event.preventDefault();
      }
      if (event.which === 38) { // up
        ctrl.focussedResult = null;
        try {
          $target
            .parent()
            .prevAll('li.result')[0]
            .children[0]
            .focus();
        } catch (e) {
          ctrl.focusLastSearchResult(event);
        }
        event.preventDefault();
      }
    };
  });
