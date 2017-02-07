'use strict';

const angular = require('angular');

import {V2_MODAL_WIZARD_SERVICE} from 'core/modal/wizard/v2modalWizard.service';
import {NAMING_SERVICE} from 'core/naming/naming.service';
import {SERVER_GROUP_COMMAND_REGISTRY_PROVIDER} from 'core/serverGroup/configure/common/serverGroupCommandRegistry.provider';

module.exports = angular
  .module('spinnaker.netflix.serverGroup.configurer.service', [
    NAMING_SERVICE,
    require('core/config/settings.js'),
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
  .run(function(serverGroupCommandRegistry, netflixServerGroupCommandConfigurer, settings) {
    if (settings.feature && settings.feature.netflixMode) {
      serverGroupCommandRegistry.register('aws', netflixServerGroupCommandConfigurer);
    }
  });
