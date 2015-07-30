'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.serverGroup.write.service', [
    require('../../services/taskExecutor.js'),
    require('./configure/common/transformer/serverGroup.transformer.service.js'),
  ])
  .factory('serverGroupWriter', function (taskExecutor, serverGroupTransformer) {

    function destroyServerGroup(serverGroup, application) {
      return taskExecutor.executeTask({
        job: [
          {
            asgName: serverGroup.name,
            type: 'destroyAsg',
            regions: [serverGroup.region],
            zones: serverGroup.zones,
            credentials: serverGroup.account,
            providerType: serverGroup.type
          }
        ],
        application: application,
        description: 'Destroy Server Group: ' + serverGroup.name
      });
    }

    function disableServerGroup(serverGroup, applicationName) {
      return taskExecutor.executeTask({
        job: [
          {
            asgName: serverGroup.name,
            type: 'disableAsg',
            regions: [serverGroup.region],
            zones: serverGroup.zones,
            credentials: serverGroup.account,
            providerType: serverGroup.type
          }
        ],
        application: applicationName,
        description: 'Disable Server Group: ' + serverGroup.name
      });
    }

    function enableServerGroup(serverGroup, application) {
      return taskExecutor.executeTask({
        job: [
          {
            asgName: serverGroup.name,
            type: 'enableAsg',
            regions: [serverGroup.region],
            zones: serverGroup.zones,
            credentials: serverGroup.account,
            providerType: serverGroup.type
          }
        ],
        application: application,
        description: 'Enable Server Group: ' + serverGroup.name
      });
    }

    function resizeServerGroup(serverGroup, capacity, application) {
      return taskExecutor.executeTask({
        job: [
          {
            asgName: serverGroup.name,
            type: 'resizeAsg',
            regions: [serverGroup.region],
            zones: serverGroup.zones,
            credentials: serverGroup.account,
            capacity: capacity,
            providerType: serverGroup.type
          }
        ],
        application: application,
        description: 'Resize Server Group: ' + serverGroup.name + ' to ' + capacity.min + '/' + capacity.desired + '/' + capacity.max
      });
    }

    function cloneServerGroup(command, application) {

      var description;
      if (command.viewState.mode === 'clone') {
        description = 'Create Cloned Server Group from ' + command.source.asgName;
        command.type = 'copyLastAsg';
      } else {
        command.type = 'linearDeploy';
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
      resizeServerGroup: resizeServerGroup,
      cloneServerGroup: cloneServerGroup
    };
  })
  .name;
