import {module} from 'angular';

import {APPLICATION_DATA_SOURCE_REGISTRY, ApplicationDataSourceRegistry} from 'core/application/service/applicationDataSource.registry';
import {CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry} from 'core/cloudProvider/cloudProvider.registry';
import {BLESK_MODULE} from './blesk/blesk.module';
import {TABLEAU_STATES} from './tableau/tableau.states';
import {ISOLATED_TESTING_TARGET_STAGE_MODULE} from './pipeline/stage/isolatedTestingTarget/isolatedTestingTargetStage.module';
import {FEEDBACK_MODULE} from './feedback/feedback.module';
import {AVAILABILITY_DIRECTIVE} from './availability/availability.directive';
import {NetflixSettings} from './netflix.settings';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function (key) {
  templates(key);
});

export const NETFLIX_MODULE = 'spinnaker.netflix';
module('spinnaker.netflix', [
  AVAILABILITY_DIRECTIVE,
  require('./whatsNew/whatsNew.directive.js'),
  BLESK_MODULE,
  require('./fastProperties/fastProperties.module.js'),
  require('./alert/alertHandler.js'),
  FEEDBACK_MODULE,
  require('./instance/aws/netflixAwsInstanceDetails.controller.js'),
  require('./instance/titus/netflixTitusInstanceDetails.controller.js'),
  require('./pipeline/stage/canary/canaryStage.module.js'),
  ISOLATED_TESTING_TARGET_STAGE_MODULE,
  require('./pipeline/stage/acaTask/acaTaskStage.module'),
  require('./pipeline/stage/properties'),
  require('./pipeline/stage/quickPatchAsg/quickPatchAsgStage.module.js'),
  require('./pipeline/stage/quickPatchAsg/bulkQuickPatchStage/bulkQuickPatchStage.module.js'),
  require('./pipeline/stage/chap/chapStage'),
  require('./pipeline/config/properties'),

  require('./canary'),
  require('./templateOverride/templateOverrides.module.js'),
  require('./migrator/pipeline/pipeline.migrator.directive.js'),
  require('./serverGroup/wizard/serverGroupCommandConfigurer.service.js'),
  require('./serverGroup/networking/networking.module.js'),
  require('./report/reservationReport.directive.js'),

  require('./application/netflixCreateApplicationModal.controller.js'),
  require('./application/netflixEditApplicationModal.controller.js'),
  require('./help/netflixHelpContents.registry.js'),
  TABLEAU_STATES,
  require('./ci/ci.module'),
  APPLICATION_DATA_SOURCE_REGISTRY,
  CLOUD_PROVIDER_REGISTRY,
])
  .run((cloudProviderRegistry: CloudProviderRegistry,
        applicationDataSourceRegistry: ApplicationDataSourceRegistry) => {
    if (NetflixSettings.feature.netflixMode) {
      cloudProviderRegistry.overrideValue(
        'aws',
        'instance.detailsTemplateUrl',
        require('./instance/aws/instanceDetails.html')
      );
      cloudProviderRegistry.overrideValue(
        'aws',
        'instance.detailsController',
        'netflixAwsInstanceDetailsCtrl'
      );
      cloudProviderRegistry.overrideValue(
        'aws',
        'serverGroup.detailsTemplateUrl',
        require('./serverGroup/awsServerGroupDetails.html')
      );
      cloudProviderRegistry.overrideValue(
        'titus',
        'instance.detailsTemplateUrl',
        require('./instance/titus/instanceDetails.html')
      );
      cloudProviderRegistry.overrideValue(
        'titus',
        'instance.detailsController',
        'netflixTitusInstanceDetailsCtrl'
      );
      applicationDataSourceRegistry.setDataSourceOrder([
        'ci', 'executions', 'serverGroups', 'loadBalancers', 'securityGroups', 'properties', 'analytics', 'tasks', 'config'
      ]);
    }
  });
