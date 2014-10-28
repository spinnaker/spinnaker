'use strict';

angular.module('deckApp')
  .controller('BuildTimelinesCtrl', function($scope, pipelines) {
    pipelines.getAll().then(function(p) {
      $scope.pipelines = p;
    });
  });
