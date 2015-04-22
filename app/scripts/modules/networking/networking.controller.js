'use strict';

angular.module('deckApp.networking.controller', [
  'ui.bootstrap',
  'deckApp.notifications',
  'deckApp.confirmationModal.service',
  'deckApp.utils.lodash',
  'deckApp.elasticIp.read.service',
  'deckApp.elasticIp.controller',
  'deckApp.networking.ip.sort.filter',
])
  .controller('networkingCtrl', function ($scope, $modal, elasticIpReader) {
    var application = $scope.application;

    function getElasticIpsForCluster() {
      var serverGroup = $scope.serverGroup;
      elasticIpReader.getElasticIpsForCluster(application.name, serverGroup.account, serverGroup.cluster, serverGroup.region).then(function (elasticIps) {
        $scope.elasticIps = elasticIps.plain();
      });
    }

    getElasticIpsForCluster();

    $scope.associateElasticIp = function associateElasticIp() {
      $modal.open({
        templateUrl: 'scripts/modules/networking/details/aws/associateElasticIp.html',
        controller: 'ElasticIpCtrl as ctrl',
        resolve: {
          application: function() { return $scope.application; },
          serverGroup: function() { return $scope.serverGroup; },
          elasticIp: function() { return { type: 'standard' }; },
          onTaskComplete: function() { return getElasticIpsForCluster; }
        }
      });
    };

    $scope.disassociateElasticIp = function disassociateElasticIp(address) {
      $modal.open({
        templateUrl: 'scripts/modules/networking/details/aws/disassociateElasticIp.html',
        controller: 'ElasticIpCtrl as ctrl',
        resolve: {
          application: function() { return $scope.application; },
          serverGroup: function() { return $scope.serverGroup; },
          elasticIp: function() { return _.find($scope.elasticIps, function (elasticIp) { return elasticIp.address === address; }); },
          onTaskComplete: function() { return getElasticIpsForCluster;}
        }
      });
    };
  });
