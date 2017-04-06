import {module} from 'angular';

import {NetflixSettings} from './netflix.settings';
import {APPLICATION_DATA_SOURCE_REGISTRY, ApplicationDataSourceRegistry} from 'core/application/service/applicationDataSource.registry';
import {AVAILABILITY_DIRECTIVE} from './availability/availability.directive';
import {BLESK_MODULE} from './blesk/blesk.module';
import {CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry} from 'core/cloudProvider/cloudProvider.registry';
import {EXCEPTION_HANDLER} from './exception/exceptionHandler';
import {FEEDBACK_MODULE} from './feedback/feedback.module';
import {ISOLATED_TESTING_TARGET_STAGE_MODULE} from './pipeline/stage/isolatedTestingTarget/isolatedTestingTargetStage.module';
import {NETFLIX_APPLICATION_MODULE} from './application';
import {TABLEAU_STATES} from './tableau/tableau.states';
import {TEMPLATE_OVERRIDES} from './templateOverride/templateOverrides.module';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function (key) {
  templates(key);
});

export const NETFLIX_MODULE = 'spinnaker.netflix';
module('spinnaker.netflix', [
  APPLICATION_DATA_SOURCE_REGISTRY,
  AVAILABILITY_DIRECTIVE,
  BLESK_MODULE,
  CLOUD_PROVIDER_REGISTRY,
  EXCEPTION_HANDLER,
  FEEDBACK_MODULE,
  ISOLATED_TESTING_TARGET_STAGE_MODULE,
  NETFLIX_APPLICATION_MODULE,
  TABLEAU_STATES,
  TEMPLATE_OVERRIDES,

  require('./fastProperties/fastProperties.module.js'),
  require('./instance/aws/netflixAwsInstanceDetails.controller.js'),
  require('./instance/titus/netflixTitusInstanceDetails.controller.js'),
  require('./pipeline/stage/canary/canaryStage.module.js'),
  require('./pipeline/stage/acaTask/acaTaskStage.module'),
  require('./pipeline/stage/properties'),
  require('./pipeline/stage/quickPatchAsg/quickPatchAsgStage.module.js'),
  require('./pipeline/stage/quickPatchAsg/bulkQuickPatchStage/bulkQuickPatchStage.module.js'),
  require('./pipeline/stage/chap/chapStage'),
  require('./pipeline/config/properties'),
  require('./canary'),
  require('./migrator/pipeline/pipeline.migrator.directive.js'),
  require('./serverGroup/wizard/serverGroupCommandConfigurer.service.js'),
  require('./serverGroup/networking/networking.module.js'),
  require('./report/reservationReport.directive.js'),
  require('./help/netflixHelpContents.registry.js'),
  require('./ci/ci.module'),
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
