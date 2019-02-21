'use strict';

const angular = require('angular');
import _ from 'lodash';

import { AccountService, CACHE_INITIALIZER_SERVICE, LOAD_BALANCER_READ_SERVICE } from '@spinnaker/core';

module.exports = angular
  .module('spinnaker.serverGroup.configure.kubernetes.configuration.service', [
    CACHE_INITIALIZER_SERVICE,
    LOAD_BALANCER_READ_SERVICE,
    require('../../image/image.reader').name,
  ])
  .factory('kubernetesServerGroupConfigurationService', [
    '$q',
    'kubernetesImageReader',
    'loadBalancerReader',
    'cacheInitializer',
    function($q, kubernetesImageReader, loadBalancerReader, cacheInitializer) {
      function configureCommand(application, command, query = '') {
        // this ensures we get the images we need when cloning or copying a server group template.
        const containers = command.containers.concat(command.initContainers || []);
        let queries = containers
          .filter(c => {
            return !c.imageDescription.fromContext && !c.imageDescription.fromArtifact;
          })
          .map(c => {
            if (c.imageDescription.fromTrigger) {
              return c.imageDescription.repository;
            } else {
              return grabImageAndTag(c.imageDescription.imageId);
            }
          });
        if (query) {
          queries.push(query);
        }

        let imagesPromise;
        if (queries.length) {
          imagesPromise = $q
            .all(
              queries.map(q =>
                kubernetesImageReader.findImages({
                  provider: 'dockerRegistry',
                  count: 50,
                  q: q,
                }),
              ),
            )
            .then(_.flatten);
        } else {
          imagesPromise = $q.when([{ message: 'Please type your search...' }]);
        }

        return $q
          .all({
            accounts: AccountService.listAccounts('kubernetes', 'v1'),
            loadBalancers: loadBalancerReader.listLoadBalancers('kubernetes'),
            allImages: imagesPromise,
          })
          .then(function(backingData) {
            backingData.filtered = {};
            backingData.securityGroups = [];

            if (command.viewState.contextImages) {
              backingData.allImages = backingData.allImages.concat(command.viewState.contextImages);
            }

            // If we search for *nginx* and *nginx:1.11.1*, we might get two copies of nginx:1.11.1.
            backingData.allImages = _.uniqWith(backingData.allImages, _.isEqual);

            var accountMap = _.fromPairs(
              _.map(backingData.accounts, function(account) {
                return [account.name, AccountService.getAccountDetails(account.name)];
              }),
            );

            return $q.all(accountMap).then(function(accountMap) {
              backingData.accountMap = accountMap;
              command.backingData = backingData;
              configureAccount(command);
              attachEventHandlers(command);
            });
          });
      }

      function grabImageAndTag(imageId) {
        return imageId.split('/').pop();
      }

      function mapImageToContainer(command) {
        return image => {
          if (image.message) {
            return image;
          }

          return {
            name: (image.repository || image.name.replace(/[.]/g, '-'))
              .replace(/_/g, '')
              .replace(/[/ ]/g, '-')
              .toLowerCase(),
            imageDescription: {
              repository: image.repository,
              tag: image.tag,
              artifactId: image.artifactId,
              imageId: command.buildImageId(image),
              registry: image.registry,
              fromArtifact: image.fromArtifact,
              fromContext: image.fromContext,
              fromTrigger: image.fromTrigger,
              cluster: image.cluster,
              account: image.account,
              pattern: image.pattern,
              stageId: image.stageId,
            },
            imagePullPolicy: 'IFNOTPRESENT',
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
              },
            ],
            livenessProbe: null,
            readinessProbe: null,
            envVars: [],
            envFrom: [],
            command: [],
            args: [],
            volumeMounts: [],
          };
        };
      }

      function getLoadBalancerNames(command) {
        return _.chain(command.backingData.loadBalancers)
          .filter({ account: command.account })
          .filter({ namespace: command.namespace })
          .map('name')
          .flattenDeep()
          .uniq()
          .value();
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

      function getValidContainers(command, containers) {
        const validContainers = [];
        const invalidContainers = [];
        containers.forEach(function(container) {
          const { fromContext, fromTrigger, fromArtifact } = container.imageDescription;
          if (fromContext || fromTrigger || fromArtifact) {
            validContainers.push(container);
          } else {
            let matchingContainers = command.backingData.filtered.containers.filter(test => {
              if (container.imageDescription.registry) {
                return test.imageDescription.imageId === container.imageDescription.imageId;
              } else {
                return _.endsWith(test.imageDescription.imageId, container.imageDescription.imageId);
              }
            });

            if (matchingContainers.length === 1) {
              validContainers.push(container);
            } else {
              invalidContainers.push(container.image);
            }
          }
        });
        return [validContainers, invalidContainers];
      }

      function configureContainers(command) {
        var result = { dirty: {} };
        angular.extend(result.dirty, configureImages(command).dirty);
        command.backingData.filtered.containers = _.map(
          command.backingData.filtered.images,
          mapImageToContainer(command),
        );

        const [validContainers, invalidContainers] = getValidContainers(command, command.containers);
        command.containers = validContainers;
        if (invalidContainers.length > 0) {
          result.dirty.containers = invalidContainers;
        }

        const [validInitContainers, invalidInitContainers] = getValidContainers(command, command.initContainers || []);
        command.initContainers = validInitContainers;
        if (invalidInitContainers.length > 0) {
          result.dirty.initContainers = invalidInitContainers;
        }
        return result;
      }

      function configureSecurityGroups(command) {
        var result = { dirty: {} };
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
        if (!_.includes(command.backingData.filtered.namespaces, command.namespace)) {
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
          var accounts = _.map(
            _.filter(command.backingData.account.dockerRegistries, function(registry) {
              return _.includes(registry.namespaces, command.namespace);
            }),
            function(registry) {
              return registry.accountName;
            },
          );
          command.backingData.filtered.images = _.filter(command.backingData.allImages, function(image) {
            const { fromContext, fromTrigger, fromArtifact, account, message } = image;
            return fromContext || fromTrigger || fromArtifact || _.includes(accounts, account) || message;
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
    },
  ]);
