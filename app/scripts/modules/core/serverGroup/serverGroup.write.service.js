'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.serverGroup.write.service', [
    require('../task/taskExecutor.js'),
    require('./serverGroup.transformer.js'),
  ])
  .factory('serverGroupWriter', function (taskExecutor, serverGroupTransformer) {

    function destroyServerGroup(serverGroup, application, params={}) {
      params.asgName = serverGroup.name;
      params.serverGroupName = serverGroup.name;
      params.type = 'destroyServerGroup';
      params.regions = [serverGroup.region];  // Needed for AWS
      params.region = serverGroup.region;     // Needed for Titan, GCE
      params.zones = serverGroup.zones;
      params.credentials = serverGroup.account;
      params.cloudProvider = serverGroup.type;

      return taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Destroy Server Group: ' + serverGroup.name
      });
    }

    function disableServerGroup(serverGroup, applicationName, params={}) {
      params.asgName = serverGroup.name;
      params.serverGroupName = serverGroup.name;
      params.type = 'disableServerGroup';
      params.regions = [serverGroup.region];
      params.region = serverGroup.region;
      params.zones = serverGroup.zones;
      params.credentials = serverGroup.account;
      params.cloudProvider = serverGroup.type;

      return taskExecutor.executeTask({
        job: [params],
        application: applicationName,
        description: 'Disable Server Group: ' + serverGroup.name
      });
    }

    function enableServerGroup(serverGroup, application, params={}) {
      params.asgName = serverGroup.name;
      params.serverGroupName = serverGroup.name;
      params.type = 'enableServerGroup';
      params.regions = [serverGroup.region];
      params.region = serverGroup.region;
      params.zones = serverGroup.zones;
      params.credentials = serverGroup.account;
      params.cloudProvider = serverGroup.type;

      return taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Enable Server Group: ' + serverGroup.name
      });
    }

    function rollbackServerGroup(serverGroup, application, params={}) {
      params.type = 'rollbackServerGroup';
      params.regions = [serverGroup.region];
      params.zones = serverGroup.zones;
      params.credentials = serverGroup.account;
      params.cloudProvider = serverGroup.type;

      return taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Rollback Server Group: ' + serverGroup.name
      });
    }

    function resizeServerGroup(serverGroup, application, params={}) {
      params.asgName = serverGroup.name;
      params.serverGroupName = serverGroup.name;
      params.type = 'resizeServerGroup';
      params.regions = [serverGroup.region];
      params.region = serverGroup.region;
      params.zones = serverGroup.zones;
      params.credentials = serverGroup.account;
      params.cloudProvider = serverGroup.type;
      return taskExecutor.executeTask({
        job: [params],
        application: application,
        description: 'Resize Server Group: ' + serverGroup.name + ' to ' + params.capacity.min + '/' + params.capacity.desired + '/' + params.capacity.max
      });
    }

    function cloneServerGroup(command, application) {
      var description;
      if (command.viewState.mode === 'clone') {
        description = 'Create Cloned Server Group from ' + command.source.asgName;
        command.type = 'cloneServerGroup';
      } else {
        command.type = 'createServerGroup';
        var asgName = application.name;
        if (command.stack) {
          asgName += '-' + command.stack;
        }
        if (!command.stack && command.freeFormDetails) {
          asgName += '-';
        }
        if (command.freeFormDetails) {
          asgName += '-' + command.freeFormDetails;
        }
        description = 'Create New Server Group in cluster ' + asgName;
      }

      return taskExecutor.executeTask({
        job: [
          serverGroupTransformer.convertServerGroupCommandToDeployConfiguration(command)
        ],
        application: application,
        description: description
      });
    }


    return {
      destroyServerGroup: destroyServerGroup,
      disableServerGroup: disableServerGroup,
      enableServerGroup: enableServerGroup,
      rollbackServerGroup: rollbackServerGroup,
      resizeServerGroup: resizeServerGroup,
      cloneServerGroup: cloneServerGroup
    };
  });
