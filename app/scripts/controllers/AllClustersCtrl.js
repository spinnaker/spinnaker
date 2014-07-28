'use strict';

angular.module('deckApp')
  .controller('AllClustersCtrl', function($scope, application) {

    $scope.sortFilter = {
      sort: 'region'
    };

    $scope.allClusters = application.data.clusters;

    $scope.getGroups = function() {
      var groups = [];

//      var grouped = _.groupBy()
//      $scope.allClusters.forEach(function(cluster) {
//
//      });

      return groups;
    };
  });
