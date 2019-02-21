'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.dcos.serverGroup.configure.healthChecks', [])
  .controller('dcosServerGroupHealthChecksController', [
    '$scope',
    function($scope) {
      var HTTP_PROTOCOL = 'HTTP';
      var HTTPS_PROTOCOL = 'HTTPS';
      var TCP_PROTOCOL = 'TCP';
      var COMMAND_PROTOCOL = 'COMMAND';
      var MESOS_HTTP_PROTOCOL = 'MESOS_HTTP';
      var MESOS_HTTPS_PROTOCOL = 'MESOS_HTTPS';
      var MESOS_TCP_PROTOCOL = 'MESOS_TCP';

      this.healthCheckProtocols = [
        HTTP_PROTOCOL,
        HTTPS_PROTOCOL,
        TCP_PROTOCOL,
        COMMAND_PROTOCOL,
        MESOS_HTTP_PROTOCOL,
        MESOS_HTTPS_PROTOCOL,
        MESOS_TCP_PROTOCOL,
      ];
      this.healthCheckPortTypes = ['Port Index', 'Port Number'];

      this.isHealthChecksValid = function(healthChecks) {
        return !(typeof healthChecks === 'string' || healthChecks instanceof String);
      };

      if (this.isHealthChecksValid($scope.command.healthChecks)) {
        $scope.command.healthChecks.forEach(hc => {
          hc.portType = hc.port ? this.healthCheckPortTypes[1] : this.healthCheckPortTypes[0];
        });
      }

      this.isHttpProtocol = function(healthCheck) {
        return (
          healthCheck.protocol === HTTP_PROTOCOL ||
          healthCheck.protocol === HTTPS_PROTOCOL ||
          healthCheck.protocol === MESOS_HTTP_PROTOCOL ||
          healthCheck.protocol === MESOS_HTTPS_PROTOCOL
        );
      };

      this.isCommandProtocol = function(healthCheck) {
        return healthCheck.protocol === COMMAND_PROTOCOL;
      };

      this.isTcpProtocol = function(healthCheck) {
        return healthCheck.protocol === TCP_PROTOCOL || healthCheck.protocol === MESOS_TCP_PROTOCOL;
      };

      // TODO can be smarter about this based on current ports defined
      this.addHealthCheck = function() {
        if (!this.isHealthChecksValid($scope.command.healthChecks)) {
          $scope.command.healthChecks = [];
        }

        $scope.command.healthChecks.push({
          protocol: MESOS_HTTP_PROTOCOL,
          path: null,
          command: null,
          gracePeriodSeconds: null,
          intervalSeconds: null,
          timeoutSeconds: null,
          maxConsecutiveFailures: null,
          portType: this.healthCheckPortTypes[0],
          port: null,
          portIndex: null,
          ignoreHttp1xx: false,
        });
      };

      this.removeHealthCheck = function(index) {
        $scope.command.healthChecks.splice(index, 1);
      };
    },
  ]);
