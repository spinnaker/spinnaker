'use strict';

angular
  .module('spinnaker.serverGroup.transformer.service')
  .factory('awsServerGroupTransformer', function () {

    function convertServerGroupCommandToDeployConfiguration(base) {
      // use _.defaults to avoid copying the backingData, which is huge and expensive to copy over
      var command = _.defaults({backingData: [], viewState: []}, base);
      if (base.viewState.mode !== 'clone') {
        delete command.source;
      }
      if (base.viewState.useAllImageSelection) {
        command.amiName = base.viewState.allImageSelection;
      }
      command.availabilityZones = {};
      command.availabilityZones[command.region] = base.availabilityZones;
      command.account = command.credentials;
      if (!command.ramdiskId) {
        delete command.ramdiskId; // TODO: clean up in kato? - should ignore if empty string
      }
      delete command.region;
      delete command.viewState;
      delete command.backingData;
      delete command.selectedProvider;
      delete command.instanceProfile;
      delete command.vpcId;

      if (!command.subnetType) {
        delete command.subnetType;
      }
      return command;
    }

    return {
      convertServerGroupCommandToDeployConfiguration: convertServerGroupCommandToDeployConfiguration
    };

  }).name;
