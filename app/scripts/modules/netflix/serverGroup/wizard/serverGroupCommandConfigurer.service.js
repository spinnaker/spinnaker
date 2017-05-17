'use strict';

const angular = require('angular');

import { NAMING_SERVICE, SERVER_GROUP_COMMAND_REGISTRY_PROVIDER, V2_MODAL_WIZARD_SERVICE } from '@spinnaker/core';
import { NetflixSettings } from 'netflix/netflix.settings';

module.exports = angular
  .module('spinnaker.netflix.serverGroup.configurer.service', [
    NAMING_SERVICE,
    SERVER_GROUP_COMMAND_REGISTRY_PROVIDER,
    V2_MODAL_WIZARD_SERVICE,
  ])
  .factory('netflixServerGroupCommandConfigurer', function() {
    let beforeConfiguration = (command) => {
      // leave user data intact on pipeline edits
      if (!command.viewState.existingPipelineCluster) {
        command.base64UserData = null;
      }
    };

    return {
      beforeConfiguration: beforeConfiguration,
    };

  })
  .run(function(serverGroupCommandRegistry, netflixServerGroupCommandConfigurer) {
    if (NetflixSettings.feature.netflixMode) {
      serverGroupCommandRegistry.register('aws', netflixServerGroupCommandConfigurer);
    }
  });
