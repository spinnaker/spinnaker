import { module } from 'angular';

import { CloudProviderRegistry } from '@spinnaker/core';

import { CLOUD_FOUNDRY_INSTANCE_DETAILS_CTRL } from './instance/details/details.controller';
import { CLOUD_FOUNDRY_LOAD_BALANCER_MODULE } from './loadBalancer/loadBalancer.module';
import { CLOUD_FOUNDRY_REACT_MODULE } from './reactShims/cf.react.module';
import { CLOUD_FOUNDRY_SERVER_GROUP_TRANSFORMER } from './serverGroup/serverGroup.transformer';
import { CLOUD_FOUNDRY_CREATE_SERVER_GROUP } from './serverGroup/configure/wizard/createServerGroupCtrl.cf';
import { CLOUD_FOUNDRY_SERVER_GROUP_COMMAND_BUILDER } from './serverGroup/configure/serverGroupCommandBuilder.service.cf';
import { SERVER_GROUP_DETAILS_MODULE } from './serverGroup/details/serverGroupDetails.module';
import { CLOUD_FOUNDRY_SEARCH_FORMATTER } from './search/searchResultFormatter';
import './help/cloudfoundry.help';

import { CloudFoundryInfoDetailsSection } from './serverGroup/details/sections/cloudFoundryInfoDetailsSection';
import { CloudFoundryServerGroupActions } from './serverGroup/details/cloudFoundryServerGroupActions';
import { cfServerGroupDetailsGetter } from './serverGroup/details/cfServerGroupDetailsGetter';

import './logo/cf.logo.less';
import { CloudFoundryNoLoadBalancerModal } from './loadBalancer/configure/cloudFoundryNoLoadBalancerModal';
import 'cloudfoundry/pipeline/config/validation/instanceSize.validator';
import 'cloudfoundry/pipeline/config/validation/cfTargetImpedance.validator';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const CLOUD_FOUNDRY_MODULE = 'spinnaker.cloudfoundry';
module(CLOUD_FOUNDRY_MODULE, [
  CLOUD_FOUNDRY_REACT_MODULE,
  CLOUD_FOUNDRY_INSTANCE_DETAILS_CTRL,
  CLOUD_FOUNDRY_LOAD_BALANCER_MODULE,
  CLOUD_FOUNDRY_SERVER_GROUP_TRANSFORMER,
  CLOUD_FOUNDRY_CREATE_SERVER_GROUP,
  CLOUD_FOUNDRY_SERVER_GROUP_COMMAND_BUILDER,
  SERVER_GROUP_DETAILS_MODULE,
  CLOUD_FOUNDRY_SEARCH_FORMATTER,
  require('./pipeline/stages/enableAsg/cloudfoundryEnableAsgStage.js').name,
  require('./pipeline/stages/disableAsg/cloudfoundryDisableAsgStage.js').name,
  require('./pipeline/stages/destroyAsg/cloudfoundryDestroyAsgStage.js').name,
  require('./pipeline/stages/resizeAsg/cloudfoundryResizeAsgStage.js').name,
  require('./pipeline/stages/rollbackCluster/cloudfoundryRollbackClusterStage.js').name,
]).config(() => {
  CloudProviderRegistry.registerProvider('cloudfoundry', {
    name: 'Cloud Foundry',
    logo: {
      path: require('./logo/cf.logo.svg'),
    },
    loadBalancer: {
      transformer: 'cfLoadBalancerTransformer',
      detailsTemplateUrl: require('./loadBalancer/details/loadBalancer.details.html'),
      detailsController: 'cfLoadBalancerDetailsCtrl',
      CreateLoadBalancerModal: CloudFoundryNoLoadBalancerModal,
    },
    serverGroup: {
      skipUpstreamStageCheck: true,
      transformer: 'cfServerGroupTransformer',
      detailsActions: CloudFoundryServerGroupActions,
      detailsGetter: cfServerGroupDetailsGetter,
      detailsSections: [CloudFoundryInfoDetailsSection],
      cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/createServerGroup.html'),
      cloneServerGroupController: 'cfCreateServerGroupCtrl',
      commandBuilder: 'cfServerGroupCommandBuilder',
      scalingActivitiesEnabled: false, // FIXME enable?
    },
    search: {
      resultFormatter: 'cfSearchResultFormatter',
    },
    instance: {
      detailsTemplateUrl: require('./instance/details/details.html'),
      detailsController: 'cloudfoundryInstanceDetailsCtrl',
    },
  });
});
