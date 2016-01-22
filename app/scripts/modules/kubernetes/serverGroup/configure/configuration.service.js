'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes.configuration.service', [
  require('../../../core/account/account.service.js'),
  require('../../image/image.reader.js'),
  require('../../../core/utils/lodash.js'),
])
  .factory('kubernetesServerGroupConfigurationService', function($q, accountService, kubernetesImageReader, _) {
    function configureCommand(application, command) {
      return $q.all({
        accounts: accountService.listAccounts('kubernetes'),
        packageImages: kubernetesImageReader.findImages(),
      }).then(function(backingData) {
        backingData.filtered = {};
        backingData.loadBalancers = [];
        backingData.securityGroups = [];
        command.backingData = backingData;

        refreshLoadBalancers(command);
        refreshSecurityGroups(command);
        configureContainers(command);

        return;
      });
    }

    function mapImageToContainer(image) {
      return { name: image.imageName,
        image: image.imageName,
        requests: {
          memory: null,
          cpu: null,
        },
        limits: {
          memory: null,
          cpu: null,
        },
        viewState: {
          configureResources: false,
        },
      };
    }

    function configureContainers(command) {
      command.backingData.filtered.containers = _.map(command.backingData.packageImages, mapImageToContainer);
    }

    function refreshLoadBalancers(command) {
      command.backingData.filtered.loadBalancers = command.backingData.loadBalancers;
    }

    function refreshSecurityGroups(command) {
      command.backingData.filtered.securityGroups = command.backingData.securityGroups;
    }

    return {
      configureCommand: configureCommand,
      refreshLoadBalancers: refreshLoadBalancers,
      refreshSecurityGroups: refreshSecurityGroups,
    };
  });
