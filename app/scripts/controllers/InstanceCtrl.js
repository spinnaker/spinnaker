'use strict';

angular.module('deckApp')
  .controller('InstanceCtrl', function($scope, instance, oortService) {

    $scope.account = instance.account;
    oortService.getInstance(instance.application, instance.account, instance.cluster, instance.serverGroup, instance.name)
      .then(function(retrieved) {
        $scope.instance = retrieved;
      });
  })
;
