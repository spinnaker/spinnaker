'use strict';

angular
  .module('deckApp.instance.write.service', [
    'deckApp.taskExecutor.service'
  ])
  .factory('instanceWriter', function (taskExecutor) {

    function terminateInstance(instance, application) {
      return taskExecutor.executeTask({
        job: [
          {
            type: 'terminateInstances',
            instanceIds: [instance.instanceId],
            launchTimes: [instance.launchTime],
            region: instance.region,
            zone: instance.placement.availabilityZone,
            credentials: instance.account,
            providerType: instance.providerType
          }
        ],
        application: application,
        description: 'Terminate instance: ' + instance.instanceId
      });
    }

    return {
      terminateInstance: terminateInstance
    };

  });
