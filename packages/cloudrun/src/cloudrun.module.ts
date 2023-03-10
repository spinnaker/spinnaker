import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { CLOUDRUN_COMPONENT_URL_DETAILS } from './common/componentUrlDetails.component';
import { CLOUDRUN_LOAD_BALANCER_CREATE_MESSAGE } from './common/loadBalancerMessage.component';
import './help/cloudrun.help';
import { CLOUDRUN_INSTANCE_DETAILS_CTRL } from './instance/details/details.controller';
import { CLOUDRUN_ALLOCATION_CONFIGURATION_ROW } from './loadBalancer/configure/wizard/allocationConfigurationRow.component';
import { CLOUDRUN_LOAD_BALANCER_BASIC_SETTINGS } from './loadBalancer/configure/wizard/basicSettings.component';
import { CLOUDRUN_STAGE_ALLOCATION_CONFIGURATION_ROW } from './loadBalancer/configure/wizard/stageAllocationConfigurationRow.component';
import { CLOUDRUN_LOAD_BALANCER_WIZARD_CTRL } from './loadBalancer/configure/wizard/wizard.controller';
import { CLOUDRUN_LOAD_BALANCER_DETAILS_CTRL } from './loadBalancer/details/details.controller';
import { CLOUDRUN_LOAD_BALANCER_TRANSFORMER } from './loadBalancer/loadBalancerTransformer';
import logo from './logo/cloudrun.logo.png';
import { CLOUDRUN_PIPELINE_MODULE } from './pipeline/pipeline.module';
import { CLOUDRUN_SERVER_GROUP_COMMAND_BUILDER } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { ServerGroupWizard } from './serverGroup/configure/wizard/serverGroupWizard';
import { CLOUDRUN_SERVER_GROUP_DETAILS_CTRL } from './serverGroup/details/details.controller';
import { CLOUDRUN_SERVER_GROUP_TRANSFORMER } from './serverGroup/serverGroupTransformer.service';

import './logo/cloudrun.logo.less';

export const CLOUDRUN_MODULE = 'spinnaker.cloudrun';

const requires = [
  CLOUDRUN_COMPONENT_URL_DETAILS,
  CLOUDRUN_SERVER_GROUP_COMMAND_BUILDER,
  CLOUDRUN_SERVER_GROUP_DETAILS_CTRL,
  CLOUDRUN_SERVER_GROUP_TRANSFORMER,
  CLOUDRUN_LOAD_BALANCER_TRANSFORMER,
  CLOUDRUN_LOAD_BALANCER_DETAILS_CTRL,
  CLOUDRUN_LOAD_BALANCER_WIZARD_CTRL,
  CLOUDRUN_LOAD_BALANCER_CREATE_MESSAGE,
  CLOUDRUN_ALLOCATION_CONFIGURATION_ROW,
  CLOUDRUN_LOAD_BALANCER_BASIC_SETTINGS,
  CLOUDRUN_STAGE_ALLOCATION_CONFIGURATION_ROW,
  CLOUDRUN_PIPELINE_MODULE,
  CLOUDRUN_INSTANCE_DETAILS_CTRL,
];

module(CLOUDRUN_MODULE, requires).config(() => {
  CloudProviderRegistry.registerProvider('cloudrun', {
    name: 'cloudrun',
    logo: {
      path: logo,
    },

    instance: {
      detailsTemplateUrl: require('./instance/details/details.html'),
      detailsController: 'cloudrunInstanceDetailsCtrl',
    },
    serverGroup: {
      CloneServerGroupModal: ServerGroupWizard,
      commandBuilder: 'cloudrunV2ServerGroupCommandBuilder',
      detailsController: 'cloudrunV2ServerGroupDetailsCtrl',
      detailsTemplateUrl: require('./serverGroup/details/details.html'),
      transformer: 'cloudrunV2ServerGroupTransformer',
      skipUpstreamStageCheck: true,
    },

    loadBalancer: {
      transformer: 'cloudrunLoadBalancerTransformer',
      createLoadBalancerTemplateUrl: require('./loadBalancer/configure/wizard/wizard.html'),
      createLoadBalancerController: 'cloudrunLoadBalancerWizardCtrl',
      detailsTemplateUrl: require('./loadBalancer/details/details.html'),
      detailsController: 'cloudrunLoadBalancerDetailsCtrl',
    },
  });
});

DeploymentStrategyRegistry.registerProvider('cloudrun', ['custom', 'redblack', 'rollingpush', 'rollingredblack']);
