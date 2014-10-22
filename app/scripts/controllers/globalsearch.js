'use strict';


angular.module('deckApp')
  .controller('GlobalSearchCtrl', function($scope, $element, infrastructureSearch) {
    var ctrl = this;
    var search = infrastructureSearch();

    $scope.showSearchResults = false;

    function reset() {
      $scope.querying = false;
      $scope.query = null;
      $scope.categories = null;
      $scope.showSearchResults = false;
      $element.find('input').focus();
    }

    this.displayResults = function() {
      if ($scope.query) {
        $scope.showSearchResults = true;
      }
    };

    this.dispatchQueryInput = function(event) {
      if ($scope.showSearchResults) {
        if (event.which === 27) { // escape
          return reset();
        }
        if (event.which === 40) { // down
          return ctrl.focusFirstSearchResult(event);
        }
        if (event.which === 38) { // up
          return ctrl.focusLastSearchResult(event);
        }
        if (event.which === 39 || event.which === 37 || event.which === 16) { // right, left, shift
          return;
        }
        if (event.which === 9) { // tab
          if (!event.shiftKey) {
            ctrl.focusFirstSearchResult(event);
          }
          return;
        }
      }
      $scope.querying = true;
      search.query($scope.query).then(function(result) {
        $scope.querying = false;
        $scope.categories = result;
        $scope.showSearchResults = !!$scope.query;
      });
    };



    this.focusFirstSearchResult = function focusFirstSearchResult() {
      event.preventDefault();
      try {
        $element.find('.dropdown-menu').find('a').first().focus();
      } catch (e) {}
    };

    this.focusLastSearchResult = function focusLastSearchResult() {
      event.preventDefault();
      try {
        $element.find('.dropdown-menu').find('a').last().focus();
      } catch (e) {}
    };

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
          ctrl.focusFirstSearchResult();
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
          ctrl.focusLastSearchResult();
        }
        event.preventDefault();
      }
    };
  });
