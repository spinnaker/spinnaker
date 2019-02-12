'use strict';

const angular = require('angular');

import { AccountService } from '@spinnaker/core';

import { DcosProviderSettings } from '../../dcos.settings';

module.exports = angular
  .module('spinnaker.dcos.serverGroupCommandBuilder.service', [])
  .factory('dcosServerGroupCommandBuilder', function($q) {
    function attemptToSetValidAccount(application, defaultAccount, defaultDcosCluster, command) {
      return AccountService.getCredentialsKeyedByAccount('dcos').then(function(dcosAccountsByName) {
        var dcosAccountNames = _.keys(dcosAccountsByName);
        var firstDcosAccount = null;

        if (application.accounts.length) {
          firstDcosAccount = _.find(application.accounts, function(applicationAccount) {
            return dcosAccountNames.includes(applicationAccount);
          });
        } else if (dcosAccountNames.length) {
          firstDcosAccount = dcosAccountNames[0];
        }

        var defaultAccountIsValid = defaultAccount && dcosAccountNames.includes(defaultAccount);

        command.account = defaultAccountIsValid
          ? defaultAccount
          : firstDcosAccount
          ? firstDcosAccount
          : 'my-dcos-account';

        attemptToSetValidDcosCluster(dcosAccountsByName, defaultDcosCluster, command);
      });
    }

    function attemptToSetValidDcosCluster(dcosAccountsByName, defaultDcosCluster, command) {
      var selectedAccount = dcosAccountsByName[command.account];
      if (selectedAccount) {
        var clusterNames = _.map(selectedAccount.dcosClusters, 'name');
        var defaultDcosClusterIsValid = defaultDcosCluster && clusterNames.includes(defaultDcosCluster);
        command.dcosCluster = defaultDcosClusterIsValid
          ? defaultDcosCluster
          : clusterNames.length == 1
          ? clusterNames[0]
          : null;
        command.region = command.dcosCluster;
      }
    }

    function reconcileUpstreamImages(image, upstreamImages) {
      if (image.fromContext) {
        let matchingImage = upstreamImages.find(otherImage => image.stageId === otherImage.stageId);

        if (matchingImage) {
          image.cluster = matchingImage.cluster;
          image.pattern = matchingImage.pattern;
          image.repository = matchingImage.repository;
          return image;
        } else {
          return null;
        }
      } else if (image.fromTrigger) {
        let matchingImage = upstreamImages.find(otherImage => {
          return (
            image.registry === otherImage.registry &&
            image.repository === otherImage.repository &&
            image.tag === otherImage.tag
          );
        });

        if (matchingImage) {
          return image;
        } else {
          return null;
        }
      } else {
        return image;
      }
    }

    function findUpstreamImages(current, all, visited = {}) {
      // This actually indicates a loop in the stage dependencies.
      if (visited[current.refId]) {
        return [];
      } else {
        visited[current.refId] = true;
      }
      let result = [];
      if (current.type === 'findImage') {
        result.push({
          fromContext: true,
          cluster: current.cluster,
          pattern: current.imageNamePattern,
          repository: current.name,
          stageId: current.refId,
        });
      }
      current.requisiteStageRefIds.forEach(function(id) {
        let next = all.find(stage => stage.refId === id);
        if (next) {
          result = result.concat(findUpstreamImages(next, all, visited));
        }
      });

      return result;
    }

    function findTriggerImages(triggers) {
      return triggers
        .filter(trigger => {
          return trigger.type === 'docker';
        })
        .map(trigger => {
          return {
            fromTrigger: true,
            repository: trigger.repository,
            account: trigger.account,
            organization: trigger.organization,
            registry: trigger.registry,
            tag: trigger.tag,
          };
        });
    }

    function buildNewServerGroupCommand(application, defaults = {}) {
      var defaultAccount = defaults.account || DcosProviderSettings.defaults.account;
      var defaultDcosCluster = defaults.dcosCluster || DcosProviderSettings.defaults.dcosCluster;

      var command = {
        application: application.name,
        group: null,
        stack: '',
        freeFormDetails: '',
        cmd: null,
        args: null,
        dcosUser: null,
        env: {},
        desiredCapacity: 1,
        cpus: 0.5,
        mem: 512,
        disk: 0.0,
        gpus: 0.0,
        constraints: '',
        fetch: null,
        storeUrls: null,
        backoffSeconds: null,
        backoffFactor: null,
        maxLaunchDelaySeconds: null,
        readinessChecks: null,
        dependencies: null,
        upgradeStrategy: null,
        acceptedResourceRoles: null,
        residency: null,
        secrets: {},
        taskKillGracePeriodSeconds: null,
        requirePorts: false,
        docker: { parameters: [] },
        labels: {},
        healthChecks: [],
        persistentVolumes: [],
        dockerVolumes: [],
        externalVolumes: [],
        networkType: 'HOST',
        serviceEndpoints: [],
        viewState: {
          mode: defaults.mode || 'create',
          disableStrategySelection: true,
        },
        cloudProvider: 'dcos',
        selectedProvider: 'dcos',
        viewModel: {},
      };

      attemptToSetValidAccount(application, defaultAccount, defaultDcosCluster, command);

      return $q.when(command);
    }

    function buildNewServerGroupCommandForPipeline(current, pipeline) {
      let contextImages = findUpstreamImages(current, pipeline.stages) || [];
      contextImages = contextImages.concat(findTriggerImages(pipeline.triggers));

      return $q.when({
        viewState: {
          contextImages: contextImages,
          mode: 'editPipeline',
          requiresTemplateSelection: true,
        },
      });
    }

    function buildServerGroupCommandFromExisting(app, existing, mode) {
      mode = mode || 'clone';

      var command = existing.deployDescription;

      command.cloudProvider = 'dcos';
      command.selectedProvider = 'dcos';
      command.account = existing.account;
      command.strategy = '';
      command.viewModel = {};

      command.viewState = {
        mode: mode,
      };

      command.source = {
        serverGroupName: existing.name,
        asgName: existing.name,
        account: existing.account,
        region: existing.region,
      };

      if (!command.capacity) {
        command.capacity = {
          min: command.desiredCapacity,
          max: command.desiredCapacity,
          desired: command.desiredCapacity,
        };
      }

      return $q.when(command);
    }

    function buildServerGroupCommandFromPipeline(application, originalCluster, current, pipeline) {
      var pipelineCluster = _.cloneDeep(originalCluster);

      var commandOptions = { account: pipelineCluster.account, region: pipelineCluster.region };
      var asyncLoader = $q.all({ command: buildNewServerGroupCommand(application, commandOptions) });

      return asyncLoader.then(function(asyncData) {
        var command = asyncData.command;

        let contextImages = findUpstreamImages(current, pipeline.stages) || [];
        contextImages = contextImages.concat(findTriggerImages(pipeline.triggers));

        if (command.docker && command.docker.image) {
          command.docker.image = reconcileUpstreamImages(command.docker.image, contextImages);
        }

        var viewState = {
          contextImages: contextImages,
          mode: 'editPipeline',
          submitButtonLabel: 'Done',
        };

        var viewOverrides = {
          region: pipelineCluster.region,
          credentials: pipelineCluster.account,
          viewState: viewState,
        };

        pipelineCluster.strategy = pipelineCluster.strategy || '';
        var extendedCommand = angular.extend({}, command, pipelineCluster, viewOverrides);
        return extendedCommand;
      });
    }

    return {
      buildNewServerGroupCommand: buildNewServerGroupCommand,
      buildNewServerGroupCommandForPipeline: buildNewServerGroupCommandForPipeline,
      buildServerGroupCommandFromExisting: buildServerGroupCommandFromExisting,
      buildServerGroupCommandFromPipeline: buildServerGroupCommandFromPipeline,
    };
  });
