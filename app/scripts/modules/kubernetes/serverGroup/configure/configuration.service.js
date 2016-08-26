'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes.configuration.service', [
  require('../../../core/account/account.service.js'),
  require('../../../core/cache/cacheInitializer.js'),
  require('../../../core/loadBalancer/loadBalancer.read.service.js'),
  require('../../../core/utils/lodash.js'),
  require('../../image/image.reader.js'),
])
  .factory('kubernetesServerGroupConfigurationService', function($q, accountService, kubernetesImageReader, _,
                                                                 loadBalancerReader, cacheInitializer) {
    function configureCommand(application, command, query = '') {

      // this ensures we get the images we need when cloning or copying a server group template.
      let queries = command.containers
        .map(c => formatQuery(grabImageAndTag(c.imageDescription.imageId)));

      queries.push(formatQuery(query));

      let imagesPromise = $q.all(queries
        .map(q => kubernetesImageReader.findImages({
          provider: 'dockerRegistry',
          count: 50,
          q: q })))
        .then(_.flatten);

      return $q.all({
        accounts: accountService.listAccounts('kubernetes'),
        loadBalancers: loadBalancerReader.listLoadBalancers('kubernetes'),
        allImages: imagesPromise
      }).then(function(backingData) {
        backingData.filtered = {};
        backingData.securityGroups = [];

        if (command.viewState.contextImages) {
          backingData.allImages = backingData.allImages.concat(command.viewState.contextImages);
        }

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

    function formatQuery(query) {
      return `*${query}*`;
    }

    function grabImageAndTag(imageId) {
      return imageId.split('/').pop();
    }

    function mapImageToContainer(command) {
      return (image) => {
        return {
          name: image.repository.replace(/_/g, '').replace(/[\/ ]/g, '-').toLowerCase(),
          imageDescription: {
            repository: image.repository,
            tag: image.tag,
            imageId: command.buildImageId(image),
            registry: image.registry,
            fromContext: image.fromContext,
            fromTrigger: image.fromTrigger,
            cluster: image.cluster,
            account: image.account,
            pattern: image.pattern,
            stageId: image.stageId,
          },
          account: image.accountName,
          requests: {
            memory: null,
            cpu: null,
          },
          limits: {
            memory: null,
            cpu: null,
          },
          ports: [
          {
            name: 'http',
            containerPort: 80,
            protocol: 'TCP',
            hostPort: null,
            hostIp: null,
          }
          ],
          livenessProbe: null,
          readinessProbe: null,
          envVars: [],
          command: [],
          args: [],
          volumeMounts: [],
        };
      };
    }

    function getLoadBalancerNames(command) {
      return _(command.backingData.loadBalancers)
        .filter({ account: command.account })
        .filter({ namespace: command.namespace })
        .pluck('name')
        .flatten(true)
        .unique()
        .valueOf();
    }

    function configureLoadBalancers(command) {
      var results = { dirty: {} };
      var current = command.loadBalancers;
      var newLoadBalancers = getLoadBalancerNames(command);

      if (current && command.loadBalancers) {
        var matched = _.intersection(newLoadBalancers, command.loadBalancers);
        var removed = _.xor(matched, current);
        command.loadBalancers = matched;
        if (removed.length) {
          results.dirty.loadBalancers = removed;
        }
      }
      command.backingData.filtered.loadBalancers = newLoadBalancers;
      return results;
    }

    function configureContainers(command) {
      var result = { dirty : {} };
      angular.extend(result.dirty, configureImages(command).dirty);
      command.backingData.filtered.containers = _.map(command.backingData.filtered.images, mapImageToContainer(command));
      var validContainers = [];
      command.containers.forEach(function(container) {
        if (container.imageDescription.fromContext
            || container.imageDescription.fromTrigger
            || _.find(command.backingData.filtered.containers, { imageDescription: { imageId: container.imageDescription.imageId } })) {
          validContainers.push(container);
        } else {
          result.dirty.containers = result.dirty.containers || [];
          result.dirty.containers.push(container.image);
        }
      });
      command.containers = validContainers;
      return result;
    }

    function configureSecurityGroups(command) {
      var result = { dirty : {} };
      command.backingData.filtered.securityGroups = command.backingData.securityGroups;
      return result;
    }

    function refreshLoadBalancers(command, skipCommandReconfiguration) {
      return cacheInitializer.refreshCache('loadBalancers').then(function() {
        return loadBalancerReader.listLoadBalancers('kubernetes').then(function(loadBalancers) {
          command.backingData.loadBalancers = loadBalancers;
          if (!skipCommandReconfiguration) {
            configureLoadBalancers(command);
          }
        });
      });
    }

    function configureNamespaces(command) {
      var result = { dirty: {} };
      command.backingData.filtered.namespaces = command.backingData.account.namespaces;
      if (!_.contains(command.backingData.filtered.namespaces, command.namespace)) {
        command.namespace = null;
        result.dirty.namespace = true;
      }
      angular.extend(result.dirty, configureContainers(command).dirty);
      angular.extend(result.dirty, configureLoadBalancers(command).dirty);
      return result;
    }

    function configureDockerRegistries(command) {
      var result = { dirty: {} };
      command.backingData.filtered.dockerRegistries = command.backingData.account.dockerRegistries;
      return result;
    }

    function configureAccount(command) {
      var result = { dirty: {} };
      command.backingData.account = command.backingData.accountMap[command.account];
      if (command.backingData.account) {
        angular.extend(result.dirty, configureDockerRegistries(command).dirty);
        angular.extend(result.dirty, configureNamespaces(command).dirty);
        angular.extend(result.dirty, configureSecurityGroups(command).dirty);
      }
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
          return image.fromContext || image.fromTrigger || _.contains(accounts, image.account);
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

      command.accountChanged = function accountChanged() {
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
      refreshLoadBalancers: refreshLoadBalancers,
    };
  });
