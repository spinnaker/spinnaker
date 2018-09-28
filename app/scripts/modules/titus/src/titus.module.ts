import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { AmazonLoadBalancersTag } from '@spinnaker/amazon';

import { TITUS_MIGRATION_CONFIG_COMPONENT } from './migration/titusMigrationConfig.component';
import './validation/ApplicationNameValidator';
import './help/titus.help';
import { TITUS_REACT_MODULE } from './reactShims/titus.react.module';
import './pipeline/stages/runJob/titusRunJobStage';

import { TitusCloneServerGroupModal } from './serverGroup/configure/wizard/TitusCloneServerGroupModal';

import './logo/titus.logo.less';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const TITUS_MODULE = 'spinnaker.titus';
module(TITUS_MODULE, [
  TITUS_REACT_MODULE,
  require('./securityGroup/securityGroup.read.service').name,
  require('./serverGroup/details/serverGroupDetails.titus.controller.js').name,
  require('./serverGroup/configure/ServerGroupCommandBuilder.js').name,
  require('./serverGroup/configure/serverGroup.configure.titus.module.js').name,
  require('./serverGroup/serverGroup.transformer.js').name,
  require('./instance/details/instance.details.controller.js').name,
  require('./pipeline/stages/findAmi/titusFindAmiStage.js').name,
  require('./pipeline/stages/enableAsg/titusEnableAsgStage.js').name,
  require('./pipeline/stages/disableAsg/titusDisableAsgStage.js').name,
  require('./pipeline/stages/destroyAsg/titusDestroyAsgStage.js').name,
  require('./pipeline/stages/resizeAsg/titusResizeAsgStage.js').name,
  require('./pipeline/stages/cloneServerGroup/titusCloneServerGroupStage.js').name,
  require('./pipeline/stages/bake/titusBakeStage.js').name,
  require('./pipeline/stages/disableCluster/titusDisableClusterStage.js').name,
  require('./pipeline/stages/shrinkCluster/titusShrinkClusterStage.js').name,
  require('./pipeline/stages/scaleDownCluster/titusScaleDownClusterStage.js').name,
  TITUS_MIGRATION_CONFIG_COMPONENT,
]).config(() => {
  CloudProviderRegistry.registerProvider('titus', {
    name: 'Titus',
    logo: {
      path: require('./logo/titus.logo.png'),
    },
    serverGroup: {
      transformer: 'titusServerGroupTransformer',
      detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
      detailsController: 'titusServerGroupDetailsCtrl',
      CloneServerGroupModal: TitusCloneServerGroupModal,
      commandBuilder: 'titusServerGroupCommandBuilder',
      configurationService: 'titusServerGroupConfigurationService',
      skipUpstreamStageCheck: true,
    },
    securityGroup: {
      reader: 'titusSecurityGroupReader',
      useProvider: 'aws',
    },
    loadBalancer: {
      LoadBalancersTag: AmazonLoadBalancersTag,
      useProvider: 'aws',
    },
    instance: {
      detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
      detailsController: 'titusInstanceDetailsCtrl',
    },
  });
});

DeploymentStrategyRegistry.registerProvider('titus', ['custom', 'redblack']);
