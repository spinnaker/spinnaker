'use strict';

angular.module('deckApp')
  .controller('ApplicationsCtrl', function($scope, applications) {
    $scope.applications = applications;

  });
