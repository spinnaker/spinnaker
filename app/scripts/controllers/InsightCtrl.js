'use strict';

angular.module('deckApp')
  .controller('InsightCtrl', function($scope, $state) {
    $scope.sortFilter = {};

    this.isSideNavHideable = function() {
      return  $state.is('home.applications.application.insight.clusters') && $scope.application.serverGroups.length === 0
    }

  });
