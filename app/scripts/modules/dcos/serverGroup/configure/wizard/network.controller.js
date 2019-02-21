'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.dcos.serverGroup.configure.network', [])
  .controller('dcosServerGroupNetworkController', ['$scope', function($scope) {
    var HOST_NETWORK = 'HOST';
    var BRIDGE_NETWORK = 'BRIDGE';
    var USER_NETWORK = 'USER';

    this.networkTypes = [
      {
        type: HOST_NETWORK,
        name: 'Host',
      },
      {
        type: BRIDGE_NETWORK,
        name: 'Bridge',
      },
      {
        type: USER_NETWORK,
        name: 'Virtual',
      },
    ];

    this.isHostNetwork = function(serviceEndpoint) {
      return serviceEndpoint === HOST_NETWORK || serviceEndpoint.networkType === HOST_NETWORK;
    };

    this.isUserNetwork = function(serviceEndpoint) {
      return serviceEndpoint === USER_NETWORK || serviceEndpoint.networkType === USER_NETWORK;
    };

    this.isServiceEndpointsValid = function(serviceEndpoints) {
      return !(typeof serviceEndpoints === 'string' || serviceEndpoints instanceof String);
    };

    this.serviceEndpointProtocols = ['tcp', 'udp', 'udp,tcp'];

    this.addServiceEndpoint = function() {
      if (!this.isServiceEndpointsValid($scope.command.serviceEndpoints)) {
        $scope.command.serviceEndpoints = [];
      }

      $scope.command.serviceEndpoints.push({
        networkType: $scope.command.networkType,
        port: null,
        name: null,
        protocol: this.serviceEndpointProtocols[0],
        loadBalanced: false,
        exposeToHost: false,
      });
    };

    this.removeServiceEndpoint = function(index) {
      $scope.command.serviceEndpoints.splice(index, 1);
    };

    this.changeNetworkType = function() {
      $scope.command.serviceEndpoints.forEach(function(endpoint) {
        endpoint.networkType = $scope.command.networkType;
      });
    };
  }]);
