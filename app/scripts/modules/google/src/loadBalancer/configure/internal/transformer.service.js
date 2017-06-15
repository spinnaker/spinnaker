'use strict';

const angular = require('angular');
import * as _ from 'lodash';

module.exports = angular.module('spinnaker.gce.deck.internalLoadBalancer.transformer', [])
  .factory('internalLoadBalancerTransformer', function () {
    // SERIALIZE

    function serialize (loadBalancer, originalListeners) {
      let commands = buildCommandForEachListener(loadBalancer);

      if (originalListeners) {
        commands[0].listenersToDelete = _.chain(originalListeners)
          .map('name')
          .difference(_.map(loadBalancer.listeners, 'name'))
          .value();
      }

      return commands;
    }

    function buildCommandForEachListener (loadBalancer) {
      return loadBalancer.listeners.map((listener) => {
        let command = _.cloneDeep(loadBalancer);
        command.name = listener.name;
        command.ipAddress = listener.ipAddress;
        command.subnet = listener.subnet;
        command.ports = listener.ports.split(',').map((port) => port.trim());
        command.cloudProvider = 'gce';
        command.backendService.name = loadBalancer.loadBalancerName;
        delete command.instances;

        return command;
      });
    }

    return { serialize };
  });
