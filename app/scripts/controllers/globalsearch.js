'use strict';

var angular = require('angular');

angular.module('deckApp')
  .controller('GlobalSearchCtrl', function($scope, $element, infrastructureSearch, $) {
    var search = infrastructureSearch(),
        elem = $($element[0]);

    $scope.showSearchResults = false;

    this.dispatchQueryInput = function(event) {
      if (event.keyIdentifier === 'Down' && $scope.showSearchResults) {
        try {
          elem.find('.dropdown-menu').find('a')[0].focus();
        } catch (e) {}
      } else {
        search.query($scope.query).then(function(result) {
          $scope.categories = result;
          $scope.showSearchResults = true;
        });
      }
    };

    this.showSearchResults = function() {
      if (angular.isObject($scope.categories)) {
        $scope.showSearchResults = true;
      }
    };

    this.navigateResults = function(event) {
      if (event.keyIdentifier === 'Down') {
        try {
          $(event.srcElement)
            .parent()
            .nextAll('.result')[0]
            .children[0]
            .focus();
        } catch (e) {}
      }
      if (event.keyIdentifier === 'Up') {
        try {
          $(event.srcElement)
            .parent()
            .prevAll('.result')[0]
            .children[0]
            .focus();
        } catch (e) {}
      }
    };
  });
