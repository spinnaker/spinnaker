'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.azure.serverGroup.details.networking.controller', [
  require('../../../../core/confirmationModal/confirmationModal.service.js'),
  require('../../../../core/utils/lodash.js'),
  require('./elasticIp.read.service.js'),
  require('./elasticIp.controller.js'),
  require('./ip.sort.filter.js'),
])
  .controller('azureNetworkingCtrl', function ($scope, $modal, elasticIpReader, _) {
    var application = $scope.application;

    function getElasticIpsForCluster() {
      var serverGroup = $scope.serverGroup;
      elasticIpReader.getElasticIpsForCluster(application.name, serverGroup.account, serverGroup.cluster, serverGroup.region).then(function (elasticIps) {
        $scope.elasticIps = elasticIps.plain ? elasticIps.plain() : [];
      });
    }

    getElasticIpsForCluster();

    $scope.associateElasticIp = function associateElasticIp() {
      $modal.open({
        templateUrl: require('./details/associateElasticIp.html'),
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
        templateUrl: require('./details/disassociateElasticIp.html'),
        controller: 'ElasticIpCtrl as ctrl',
        resolve: {
          application: function() { return $scope.application; },
          serverGroup: function() { return $scope.serverGroup; },
          elasticIp: function() { return _.find($scope.elasticIps, function (elasticIp) { return elasticIp.address === address; }); },
          onTaskComplete: function() { return getElasticIpsForCluster; }
        }
      });
    };
  });
