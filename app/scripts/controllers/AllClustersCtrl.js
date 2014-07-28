'use strict';

angular.module('deckApp')
  .controller('AllClustersCtrl', function($scope, application) {
    $scope.listClusters = function() {
      console.warn('application:', application);
      return application.data.clusters;
    };
  });
