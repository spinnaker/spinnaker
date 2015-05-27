'use strict';


angular.module('spinnaker.search.global')
  .controller('GlobalSearchCtrl', function($scope, $element, $window, infrastructureSearchService, ClusterFilterModel, $stateParams, _, clusterFilterService) {
    var ctrl = this;
    var search = infrastructureSearchService();

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
          $scope.categories = result;
          $scope.showSearchResults = !!$scope.query;
        });
      });
    }, 200);

    ctrl.clearFilters = function(result) {
      if (result.href.indexOf('/clusters') !== -1) {
        ClusterFilterModel.clearFilters();
        ClusterFilterModel.sortFilter.filter = result.serverGroup ? result.serverGroup :
            result.cluster ? 'cluster:' + result.cluster : '';
        if (result.account) {
          var acct = {};
          acct[result.account] = true;
          ClusterFilterModel.sortFilter.account = acct;
        }
        if (result.region) {
          var reg = {};
          reg[result.region] = true;
          ClusterFilterModel.sortFilter.region = reg;
        }
        if ($stateParams.application === result.application) {
          clusterFilterService.updateClusterGroups();
        }
      }
    };

    this.focusFirstSearchResult = function focusFirstSearchResult(event) {
      try {
        event.preventDefault();
        $element.find('ul.dropdown-menu').find('a').first().focus();
      } catch (e) {}
    };

    this.focusLastSearchResult = function focusLastSearchResult(event) {
      try {
        event.preventDefault();
        $element.find('ul.dropdown-menu').find('a').last().focus();
      } catch (e) {}
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
