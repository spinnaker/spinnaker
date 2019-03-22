import { module } from 'angular';

import { CloudProviderRegistry } from '@spinnaker/core';

import { CLOUD_FOUNDRY_LOAD_BALANCER_MODULE } from './loadBalancer/loadBalancer.module';
import { CLOUD_FOUNDRY_REACT_MODULE } from './reactShims/cf.react.module';
import { CLOUD_FOUNDRY_SERVER_GROUP_TRANSFORMER } from './serverGroup/serverGroup.transformer';
import { CLOUD_FOUNDRY_SERVER_GROUP_COMMAND_BUILDER } from './serverGroup/configure/serverGroupCommandBuilder.service.cf';
import { CLOUD_FOUNDRY_SEARCH_FORMATTER } from './search/searchResultFormatter';
import './help/cloudfoundry.help';

import {
  ServerGroupInformationSection,
  ApplicationManagerSection,
  MetricsSection,
  ServerGroupSizingSection,
  HealthCheckSection,
  PackageSection,
  BoundServicesSection,
  EvironmentVariablesSection,
} from 'cloudfoundry/serverGroup';
import { CloudFoundryServerGroupActions } from './serverGroup/details/cloudFoundryServerGroupActions';
import { cfServerGroupDetailsGetter } from './serverGroup/details/cfServerGroupDetailsGetter';

import './logo/cf.logo.less';
import { CloudFoundryNoLoadBalancerModal } from './loadBalancer/configure/cloudFoundryNoLoadBalancerModal';
import 'cloudfoundry/pipeline/config/validation/cfTargetImpedance.validator';
import 'cloudfoundry/pipeline/config/validation/instanceSize.validator';
import 'cloudfoundry/pipeline/config/validation/requiredRoutes.validator';
import { CLOUD_FOUNDRY_CLONE_SERVER_GROUP_STAGE } from './pipeline/stages/cloneServerGroup/cloudfoundryCloneServerGroupStage.module';
import './pipeline/stages/deployService/cloudfoundryDeployServiceStage.module';
import { CLOUD_FOUNDRY_DESTROY_ASG_STAGE } from './pipeline/stages/destroyAsg/cloudfoundryDestroyAsgStage.module';
import './pipeline/stages/destroyService/cloudfoundryDestroyServiceStage.module';
import { CLOUD_FOUNDRY_DISABLE_ASG_STAGE } from './pipeline/stages/disableAsg/cloudfoundryDisableAsgStage.module';
import { CLOUD_FOUNDRY_ENABLE_ASG_STAGE } from './pipeline/stages/enableAsg/cloudfoundryEnableAsgStage.module';
import './pipeline/stages/mapLoadBalancers/cloudfoundryMapLoadBalancersStage.module';
import './pipeline/stages/unmapLoadBalancers/cloudfoundryUnmapLoadBalancersStage.module';
import { CLOUD_FOUNDRY_RESIZE_ASG_STAGE } from './pipeline/stages/resizeAsg/cloudfoundryResizeAsgStage.module';
import { CLOUD_FOUNDRY_ROLLBACK_CLUSTER_STAGE } from './pipeline/stages/rollbackCluster/cloudfoundryRollbackClusterStage.module';
import './pipeline/stages/shareService/cloudfoundryShareServiceStage.module';
import './pipeline/stages/unshareService/cloudfoundryUnshareServiceStage.module';
import { CloudFoundryCreateServerGroupModal } from 'cloudfoundry/serverGroup/configure/wizard/CreateServerGroupModal';
import { CLOUD_FOUNDRY_INSTANCE_DETAILS } from 'cloudfoundry/instance/details/cloudfoundryInstanceDetails.module';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const CLOUD_FOUNDRY_MODULE = 'spinnaker.cloudfoundry';
module(CLOUD_FOUNDRY_MODULE, [
  CLOUD_FOUNDRY_CLONE_SERVER_GROUP_STAGE,
  CLOUD_FOUNDRY_DESTROY_ASG_STAGE,
  CLOUD_FOUNDRY_DISABLE_ASG_STAGE,
  CLOUD_FOUNDRY_ENABLE_ASG_STAGE,
  CLOUD_FOUNDRY_INSTANCE_DETAILS,
  CLOUD_FOUNDRY_LOAD_BALANCER_MODULE,
  CLOUD_FOUNDRY_REACT_MODULE,
  CLOUD_FOUNDRY_RESIZE_ASG_STAGE,
  CLOUD_FOUNDRY_ROLLBACK_CLUSTER_STAGE,
  CLOUD_FOUNDRY_SEARCH_FORMATTER,
  CLOUD_FOUNDRY_SERVER_GROUP_COMMAND_BUILDER,
  CLOUD_FOUNDRY_SERVER_GROUP_TRANSFORMER,
]).config(() => {
  CloudProviderRegistry.registerProvider('cloudfoundry', {
    name: 'Cloud Foundry',
    logo: {
      path: require('./logo/cf.logo.svg'),
    },
    loadBalancer: {
      transformer: 'cfLoadBalancerTransformer',
      detailsTemplateUrl: require('./loadBalancer/details/cloudFoundryLoadBalancerDetails.html'),
      detailsController: 'cloudfoundryLoadBalancerDetailsCtrl',
      CreateLoadBalancerModal: CloudFoundryNoLoadBalancerModal,
    },
    serverGroup: {
      skipUpstreamStageCheck: true,
      transformer: 'cfServerGroupTransformer',
      detailsActions: CloudFoundryServerGroupActions,
      detailsGetter: cfServerGroupDetailsGetter,
      detailsSections: [
        ServerGroupInformationSection,
        ApplicationManagerSection,
        MetricsSection,
        ServerGroupSizingSection,
        HealthCheckSection,
        PackageSection,
        BoundServicesSection,
        EvironmentVariablesSection,
      ],
      CloneServerGroupModal: CloudFoundryCreateServerGroupModal,
      commandBuilder: 'cfServerGroupCommandBuilder',
      scalingActivitiesEnabled: false, // FIXME enable?
    },
    search: {
      resultFormatter: 'cfSearchResultFormatter',
    },
    instance: {
      detailsTemplateUrl: require('./instance/details/cloudFoundryInstanceDetails.html'),
      detailsController: 'cfInstanceDetailsCtrl',
    },
  });
});
