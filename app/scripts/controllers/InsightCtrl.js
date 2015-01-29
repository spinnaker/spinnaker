'use strict';

angular.module('deckApp')
  .controller('InsightCtrl', function($scope, $state) {
    var self = this;
    $scope.sortFilter = {};

    function isSideNavHideable() {
      return  $state.is('home.applications.application.insight.clusters') &&
        $scope.application.serverGroups &&
        $scope.application.serverGroups.length === 0;
    }

    $scope.$on('$stateChangeSuccess', function() {
      self.isSideNavHideable = isSideNavHideable();
    });


  });
