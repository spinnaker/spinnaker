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
        allImages: kubernetesImageReader.findImages({ provider: 'dockerRegistry' }),
      }).then(function(backingData) {
        backingData.filtered = {};
        backingData.loadBalancers = [];
        backingData.securityGroups = [];
        command.backingData = backingData;

        var accountMap = _.object(_.map(backingData.accounts, function(account) {
          return [account.name, accountService.getAccountDetails(account.name)];
        }));

        return $q.all(accountMap).then(function(accountMap) {
          command.backingData.accountMap = accountMap;
          configureAccount(command);
          attachEventHandlers(command);
        });
      });
    }

    function mapImageToContainer(image) {
      return {
        name: image.imageName.replace(/\W/g, '').toLowerCase(),
        image: image.imageName,
        account: image.accountName,
        requests: {
          memory: null,
          cpu: null,
        },
        limits: {
          memory: null,
          cpu: null,
        },
      };
    }

    function configureContainers(command) {
      var result = { dirty : {} };
      angular.extend(result.dirty, configureImages(command).dirty);
      command.backingData.filtered.containers = _.map(command.backingData.filtered.images, mapImageToContainer);
      var validContainers = [];
      command.containers.forEach(function(container) {
        if (_.find(command.backingData.filtered.containers, { image: container.image })) {
          validContainers.push(container);
        } else {
          result.dirty.containers = true;
        }
      });
      command.containers = validContainers;
      return result;
    }

    function configureLoadBalancers(command) {
      var result = { dirty : {} };
      command.backingData.filtered.loadBalancers = command.backingData.loadBalancers;
      return result;
    }

    function configureSecurityGroups(command) {
      var result = { dirty : {} };
      command.backingData.filtered.securityGroups = command.backingData.securityGroups;
      return result;
    }

    /* TODO(lwander) To be incorporated later.
    function refreshNamespaces(command) {
      return accountService.getAccountDetails(command.credentials).then(function(details) {
        command.backingData.filtered.namespaces = details.namespaces;
      });
    }

    function refreshDockerRegistries(command) {
      return accountService.getAccountDetails(command.credentials).then(function(details) {
        command.backingData.filtered.dockerRegistries = details.dockerRegistries;
      });
    }
    */

    function configureNamespaces(command) {
      var result = { dirty: {} };
      command.backingData.filtered.namespaces = command.backingData.account.namespaces;
      if (!_.contains(command.backingData.filtered.namespaces, command.namespace)) {
        command.namespace = null;
        result.dirty.namespace = true;
      }
      angular.extend(result.dirty, configureContainers(command).dirty);
      return result;
    }

    function configureDockerRegistries(command) {
      var result = { dirty: {} };
      command.backingData.filtered.dockerRegistries = command.backingData.account.dockerRegistries;
      return result;
    }

    function configureAccount(command) {
      var result = { dirty: {} };
      command.backingData.account = command.backingData.accountMap[command.credentials];
      angular.extend(result.dirty, configureDockerRegistries(command).dirty);
      angular.extend(result.dirty, configureNamespaces(command).dirty);
      angular.extend(result.dirty, configureLoadBalancers(command).dirty);
      angular.extend(result.dirty, configureSecurityGroups(command).dirty);
      return result;
    }

    function configureImages(command) {
      var result = { dirty: {} };
      if (!command.namespace) {
        command.backingData.filtered.images = [];
      } else {
        var accounts = _.map(_.filter(command.backingData.account.dockerRegistries, function(registry) {
          return _.contains(registry.namespaces, command.namespace);
        }), function(registry) {
          return registry.accountName;
        });
        command.backingData.filtered.images = _.filter(command.backingData.allImages, function(image) {
          return _.contains(accounts, image.account);
        });
      }
      return result;
    }

    function attachEventHandlers(command) {
      command.namespaceChanged = function namespaceChanged() {
        var result = { dirty: {} };
        angular.extend(result.dirty, configureNamespaces(command).dirty);
        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);
        return result;
      };

      command.credentialsChanged = function credentialsChanged() {
        var result = { dirty: {} };
        angular.extend(result.dirty, configureAccount(command).dirty);
        command.viewState.dirty = command.viewState.dirty || {};
        angular.extend(command.viewState.dirty, result.dirty);
        return result;
      };
    }

    return {
      configureCommand: configureCommand,
      configureLoadBalancers: configureLoadBalancers,
      configureSecurityGroups: configureSecurityGroups,
      configureNamespaces: configureNamespaces,
      configureDockerRegistries: configureDockerRegistries,
      configureAccount: configureAccount,
    };
  });
