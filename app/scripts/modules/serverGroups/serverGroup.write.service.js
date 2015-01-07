'use strict';

angular
  .module('deckApp.serverGroup.write.service', ['deckApp.serverGroup.transformer.service'])
  .factory('serverGroupWriter', function (taskExecutor, serverGroupTransformer) {

    function destroyServerGroup(serverGroup, applicationName) {
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
        application: applicationName,
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

    function enableServerGroup(serverGroup, applicationName) {
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
        application: applicationName,
        description: 'Enable Server Group: ' + serverGroup.name
      });
    }

    function resizeServerGroup(serverGroup, capacity, applicationName) {
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
        application: applicationName,
        description: 'Resize Server Group: ' + serverGroup.name + ' to ' + capacity.min + '/' + capacity.desired + '/' + capacity.max
      });
    }

    function cloneServerGroup(command, applicationName) {

      var description;
      if (command.viewState.mode === 'clone') {
        description = 'Create Cloned Server Group from ' + command.source.asgName;
        command.type = 'copyLastAsg';
      } else {
        command.type = 'deploy';
        var asgName = applicationName;
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
        application: applicationName,
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
  });