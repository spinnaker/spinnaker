'use strict';

import { module } from 'angular';

export const DCOS_SERVERGROUP_CONFIGURE_WIZARD_HEALTHCHECKS_CONTROLLER =
  'spinnaker.dcos.serverGroup.configure.healthChecks';
export const name = DCOS_SERVERGROUP_CONFIGURE_WIZARD_HEALTHCHECKS_CONTROLLER; // for backwards compatibility
module(DCOS_SERVERGROUP_CONFIGURE_WIZARD_HEALTHCHECKS_CONTROLLER, []).controller(
  'dcosServerGroupHealthChecksController',
  [
    '$scope',
    function ($scope) {
      const HTTP_PROTOCOL = 'HTTP';
      const HTTPS_PROTOCOL = 'HTTPS';
      const TCP_PROTOCOL = 'TCP';
      const COMMAND_PROTOCOL = 'COMMAND';
      const MESOS_HTTP_PROTOCOL = 'MESOS_HTTP';
      const MESOS_HTTPS_PROTOCOL = 'MESOS_HTTPS';
      const MESOS_TCP_PROTOCOL = 'MESOS_TCP';

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

      this.isHealthChecksValid = function (healthChecks) {
        return !(typeof healthChecks === 'string' || healthChecks instanceof String);
      };

      if (this.isHealthChecksValid($scope.command.healthChecks)) {
        $scope.command.healthChecks.forEach((hc) => {
          hc.portType = hc.port ? this.healthCheckPortTypes[1] : this.healthCheckPortTypes[0];
        });
      }

      this.isHttpProtocol = function (healthCheck) {
        return (
          healthCheck.protocol === HTTP_PROTOCOL ||
          healthCheck.protocol === HTTPS_PROTOCOL ||
          healthCheck.protocol === MESOS_HTTP_PROTOCOL ||
          healthCheck.protocol === MESOS_HTTPS_PROTOCOL
        );
      };

      this.isCommandProtocol = function (healthCheck) {
        return healthCheck.protocol === COMMAND_PROTOCOL;
      };

      this.isTcpProtocol = function (healthCheck) {
        return healthCheck.protocol === TCP_PROTOCOL || healthCheck.protocol === MESOS_TCP_PROTOCOL;
      };

      // TODO can be smarter about this based on current ports defined
      this.addHealthCheck = function () {
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

      this.removeHealthCheck = function (index) {
        $scope.command.healthChecks.splice(index, 1);
      };
    },
  ],
);
