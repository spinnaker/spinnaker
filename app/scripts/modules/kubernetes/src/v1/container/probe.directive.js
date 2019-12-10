'use strict';

import { module } from 'angular';

export const KUBERNETES_V1_CONTAINER_PROBE_DIRECTIVE = 'spinnaker.kubernetes.container.probe.directive';
export const name = KUBERNETES_V1_CONTAINER_PROBE_DIRECTIVE; // for backwards compatibility
module(KUBERNETES_V1_CONTAINER_PROBE_DIRECTIVE, [])
  .directive('kubernetesContainerProbe', function() {
    return {
      restrict: 'E',
      templateUrl: require('./probe.directive.html'),
      scope: {
        container: '=',
        command: '=',
        probetype: '=',
        heading: '=',
      },
    };
  })
  .controller('kubernetesContainerProbeController', [
    '$scope',
    function($scope) {
      this.uriSchemes = ['HTTP', 'HTTPS'];
      this.maxPort = 65535;
      this.handlers = ['EXEC', 'HTTP', 'TCP'];

      this.heading = $scope.heading;
      this.probe = $scope.container[$scope.probetype];
      this.probetype = $scope.probetype;

      function defaultExecAction() {
        return { commands: [] };
      }

      function defaultHttpGetAction() {
        return {
          path: '/',
          port: 80,
          uriScheme: 'HTTP',
        };
      }

      function defaultTcpSocketAction() {
        return { port: 80 };
      }

      this.defaultProbe = function() {
        return {
          initialDelaySeconds: 0,
          timeoutSeconds: 1,
          periodSeconds: 10,
          successThreshold: 1,
          failureThreshold: 3,
          handler: {
            type: this.handlers[1],
            execAction: defaultExecAction(),
            httpGetAction: defaultHttpGetAction(),
            tcpSocketAction: defaultTcpSocketAction(),
          },
        };
      };

      this.addProbe = function() {
        $scope.container[$scope.probetype] = this.defaultProbe();
        this.probe = $scope.container[$scope.probetype];
      };

      this.deleteProbe = function() {
        delete $scope.container[$scope.probetype];
        this.probe = null;
      };

      this.removeCommand = function(index) {
        this.probe.handler.execAction.commands.splice(index, 1);
      };

      this.addCommand = function() {
        this.probe.handler.execAction.commands.push('');
      };

      this.prepareProbe = function() {
        if (this.probe) {
          if (!this.probe.handler.execAction) {
            this.probe.handler.execAction = defaultExecAction();
          }

          if (!this.probe.handler.httpGetAction) {
            this.probe.handler.httpGetAction = defaultHttpGetAction();
          }

          if (!this.probe.handler.tcpSocketAction) {
            this.probe.handler.tcpSocketAction = defaultTcpSocketAction();
          }
        }
      };

      this.addHttpHeader = function(getAction) {
        getAction.httpHeaders = getAction.httpHeaders || [];
        getAction.httpHeaders.push({});
      };

      this.deleteHttpHeader = function(getAction, index) {
        if (getAction.httpHeaders.length < 2) {
          delete getAction.httpHeaders;
        } else {
          getAction.httpHeaders.splice(index, 1);
        }
      };

      this.prepareProbe();
    },
  ]);
