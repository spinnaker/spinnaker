'use strict';

/* eslint consistent-return:0 */

let angular = require('angular');

module.exports = angular.module('spinnaker.core.search.global.controller', [
  require('angulartics'),
  require('../../utils/jQuery.js'),
  require('../searchResult/searchResult.directive.js'),
  require('../searchRank.filter.js'),
  require('../../history/recentHistory.service.js'),
])
  .controller('GlobalSearchCtrl', function($scope, $element, infrastructureSearchService, recentHistoryService,
                                           $stateParams, _, $, $log, clusterFilterService, $analytics, $sce) {
    var ctrl = this;
    var search = infrastructureSearchService();

    $scope.showSearchResults = false;

    ctrl.tooltip = $sce.trustAsHtml('Keyboard shortcut: <span class="keyboard-key">/</span>');

    function reset() {
      $scope.querying = false;
      $scope.query = null;
      $scope.categories = null;
      $scope.showSearchResults = false;
      $scope.showRecentItems = false;
      ctrl.focussedResult = null;
      $element.find('input').focus();
    }

    this.searchFieldBlurred = ($blurEvent) => {
      // if the target is outside the global search (e.g. shift+tab), hide the results
      if (!$.contains($element.get(0), $blurEvent.relatedTarget)) {
        this.hideResults();
      }
    };

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
          $analytics.eventTrack('Global Search', {category: 'Keyboard Nav', label: 'escape (from input)'});
          return reset();
        }
        if (code === 40) { // down
          $analytics.eventTrack('Global Search', {category: 'Keyboard Nav', label: 'arrow down (from input)'});
          return ctrl.focusFirstSearchResult(event);
        }
        if (code === 38) { // up
          $analytics.eventTrack('Global Search', {category: 'Keyboard Nav', label: 'arrow up (from input)'});
          return ctrl.focusLastSearchResult(event);
        }
        if (code === 9) { // tab
          if (!event.shiftKey) {
            $analytics.eventTrack('Global Search', {category: 'Keyboard Nav', label: 'tab (from input)'});
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
      $analytics.eventTrack('Global Search', {category: 'Query', label: $scope.query});
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
        $log.debug(e);
      }
    };

    this.focusLastSearchResult = function focusLastSearchResult(event) {
      try {
        event.preventDefault();
        $element.find('ul.dropdown-menu').find('a').last().focus();
      } catch (e) {
        $log.debug(e);
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
        $analytics.eventTrack('Global Search', {category: 'Keyboard Nav', label: 'escape (from result)'});
        reset();
      }
      if (event.which === 9) { // tab - let it navigate automatically, but close menu if on the last result
        if ($element.find('ul.dropdown-menu').find('a').last().is(':focus')) {
          $analytics.eventTrack('Global Search', {category: 'Keyboard Nav', label: 'tab (from result)'});
          ctrl.hideResults();
          return;
        }
      }
      if (event.which === 40) { // down
        $analytics.eventTrack('Global Search', {category: 'Keyboard Nav', label: 'down (from result)'});
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
        $analytics.eventTrack('Global Search', {category: 'Keyboard Nav', label: 'up (from result)'});
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
