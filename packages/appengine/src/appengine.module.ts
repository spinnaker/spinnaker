import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { APPENGINE_COMPONENT_URL_DETAILS } from './common/componentUrlDetails.component';
import { APPENGINE_CONDITIONAL_DESCRIPTION_LIST_ITEM } from './common/conditionalDescriptionListItem.component';
import { APPENGINE_LOAD_BALANCER_CREATE_MESSAGE } from './common/loadBalancerMessage.component';
import './helpContents/appengineHelpContents';
import { APPENGINE_INSTANCE_DETAILS_CTRL } from './instance/details/details.controller';
import { APPENGINE_LOAD_BALANCER_MODULE } from './loadBalancer/loadBalancer.module';
import logo from './logo/appengine.logo.png';
import { APPENGINE_PIPELINE_MODULE } from './pipeline/pipeline.module';
import './pipeline/stages/deployAppengineConfig/deployAppengineConfigStage';
import { APPENGINE_SERVER_GROUP_COMMAND_BUILDER } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL } from './serverGroup/configure/wizard/basicSettings.controller';
import { APPENGINE_CLONE_SERVER_GROUP_CTRL } from './serverGroup/configure/wizard/cloneServerGroup.controller';
import { CONFIG_FILE_ARTIFACT_LIST } from './serverGroup/configure/wizard/configFileArtifactList.module';
import { APPENGINE_SERVER_GROUP_DETAILS_CTRL } from './serverGroup/details/details.controller';
import { APPENGINE_SERVER_GROUP_TRANSFORMER } from './serverGroup/transformer';
import { APPENGINE_SERVER_GROUP_WRITER } from './serverGroup/writer/serverGroup.write.service';
import './validation/ApplicationNameValidator';

import './logo/appengine.logo.less';

export const APPENGINE_MODULE = 'spinnaker.appengine';

module(APPENGINE_MODULE, [
  APPENGINE_CLONE_SERVER_GROUP_CTRL,
  APPENGINE_COMPONENT_URL_DETAILS,
  APPENGINE_CONDITIONAL_DESCRIPTION_LIST_ITEM,
  APPENGINE_INSTANCE_DETAILS_CTRL,
  APPENGINE_LOAD_BALANCER_CREATE_MESSAGE,
  APPENGINE_LOAD_BALANCER_MODULE,
  APPENGINE_PIPELINE_MODULE,
  APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL,
  APPENGINE_SERVER_GROUP_COMMAND_BUILDER,
  APPENGINE_SERVER_GROUP_DETAILS_CTRL,
  APPENGINE_SERVER_GROUP_TRANSFORMER,
  APPENGINE_SERVER_GROUP_WRITER,
  CONFIG_FILE_ARTIFACT_LIST,
]).config(() => {
  CloudProviderRegistry.registerProvider('appengine', {
    name: 'App Engine',
    instance: {
      detailsTemplateUrl: require('./instance/details/details.html'),
      detailsController: 'appengineInstanceDetailsCtrl',
    },
    serverGroup: {
      transformer: 'appengineServerGroupTransformer',
      detailsController: 'appengineServerGroupDetailsCtrl',
      detailsTemplateUrl: require('./serverGroup/details/details.html'),
      commandBuilder: 'appengineServerGroupCommandBuilder',
      cloneServerGroupController: 'appengineCloneServerGroupCtrl',
      cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
      skipUpstreamStageCheck: true,
    },
    loadBalancer: {
      transformer: 'appengineLoadBalancerTransformer',
      createLoadBalancerTemplateUrl: require('./loadBalancer/configure/wizard/wizard.html'),
      createLoadBalancerController: 'appengineLoadBalancerWizardCtrl',
      detailsTemplateUrl: require('./loadBalancer/details/details.html'),
      detailsController: 'appengineLoadBalancerDetailsCtrl',
    },
    logo: {
      path: logo,
    },
  });
});

DeploymentStrategyRegistry.registerProvider('appengine', ['custom']);
