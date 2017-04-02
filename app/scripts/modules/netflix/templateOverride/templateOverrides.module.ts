import {module} from 'angular';

import {OVERRIDE_REGISTRY, OverrideRegistry} from 'core/overrideRegistry/override.registry';
import {NetflixSettings} from '../netflix.settings';

export const TEMPLATE_OVERRIDES = 'spinnaker.netflix.templateOverride.templateOverrides';
module(TEMPLATE_OVERRIDES, [OVERRIDE_REGISTRY])
  .run((overrideRegistry: OverrideRegistry) => {

    if (NetflixSettings.feature.netflixMode) {
      const templates = [
        {key: 'applicationConfigView', value: require('../application/applicationConfig.html')},
        {key: 'applicationAttributesDirective', value: require('../application/applicationAttributes.directive.html')},
        {key: 'createApplicationModal', value: require('../application/createApplication.modal.html')},
        {key: 'editApplicationModal', value: require('../application/editApplication.modal.html')},
        {key: 'pipelineConfigActions', value: require('./pipelineConfigActions.html')},
        {key: 'spinnakerHeader', value: require('./spinnakerHeader.html')},
        {
          key: 'aws.serverGroup.securityGroups',
          value: require('../serverGroup/wizard/securityGroups/awsServerGroupSecurityGroups.html')
        },
        {key: 'aws.serverGroup.capacity', value: require('../serverGroup/wizard/capacity/awsServerGroupCapacity.html')},
        {
          key: 'aws.serverGroup.advancedSettings',
          value: require('../serverGroup/wizard/advancedSettings/advancedSettings.html')
        },
        {key: 'aws.resize.modal', value: require('../serverGroup/resize/awsResizeServerGroup.html')},
      ];
      templates.forEach((template) => overrideRegistry.overrideTemplate(template.key, template.value));

      const controllers = [
        {key: 'CreateApplicationModalCtrl', value: 'netflixCreateApplicationModalCtrl'},
        {key: 'EditApplicationController', value: 'netflixEditApplicationController'},
      ];
      controllers.forEach((ctrl) => overrideRegistry.overrideController(ctrl.key, ctrl.value));
    }
  });
