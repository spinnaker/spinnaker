'use strict';

var angular = require('angular');

angular.module('deckApp')
  .controller('GlobalSearchCtrl', function($scope, $element, infrastructureSearch, $) {
    var search = infrastructureSearch();

    $scope.showSearchResults = false;

    function reset() {
      $scope.query = null;
      $scope.categories = null;
      $scope.showSearchResults = false;
      $element.find('input').focus();
    }

    this.dispatchQueryInput = function(event) {
      if (!$scope.query) {
        $scope.showSearchResults = false;
        $scope.categories = null;
        return;
      }
      if ($scope.showSearchResults) {
        if (event.which === 27) {
          return reset();
        }
        if (event.which === 40) {
          return focusFirstSearchResult(event);
        }
        if (event.which === 38) {
          return focusLastSearchResult(event);
        }
        if (event.which === 9) {
          if (!event.shiftKey) {
            focusFirstSearchResult(event);
          }
          return;
        }
      }
      search.query($scope.query).then(function(result) {
        $scope.categories = result;
        $scope.showSearchResults = true;
      });
    };

    function focusFirstSearchResult() {
      event.preventDefault();
      try {
        $element.find('.dropdown-menu').find('a').first().focus();
      } catch (e) {}
    }

    function focusLastSearchResult() {
      event.preventDefault();
      try {
        $element.find('.dropdown-menu').find('a').last().focus();
      } catch (e) {}
    }

    this.showSearchResults = function() {
      if (angular.isObject($scope.categories)) {
        $scope.showSearchResults = true;
      }
    };

    this.navigateResults = function(event) {
      var $target = $(event.target);
      if (event.which === 27) {
        reset();
      }
      if (event.which === 40) {
        try {
          $target
            .parent()
            .nextAll('.result')[0]
            .children[0]
            .focus();
        } catch (e) {
          focusFirstSearchResult();
        }
        event.preventDefault();
      }
      if (event.which === 38) {
        try {
          $target
            .parent()
            .prevAll('.result')[0]
            .children[0]
            .focus();
        } catch (e) {
          focusLastSearchResult();
        }
        event.preventDefault();
      }
    };
  });
