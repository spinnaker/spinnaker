'use strict';

angular.module('deckApp')
  .controller('ServerGroupCtrl', function($scope, account, cluster, application, serverGroup) {

    $scope.account = account.name;
    $scope.cluster = cluster;
    $scope.serverGroup = serverGroup.data[0];

    delete $scope.serverGroup.launchConfig.userData;

  })
;
