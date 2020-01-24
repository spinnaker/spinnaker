import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';
import { AmazonLoadBalancersTag } from '@spinnaker/amazon';

import { TITUS_SERVERGROUP_DETAILS_CAPACITYDETAILSSECTION } from './serverGroup/details/capacityDetailsSection.component';
import { TITUS_SERVERGROUP_DETAILS_LAUNCHCONFIGSECTION } from './serverGroup/details/launchConfigSection.component';
import './validation/ApplicationNameValidator';
import './help/titus.help';
import { TITUS_REACT_MODULE } from './reactShims/titus.react.module';
import './pipeline/stages/runJob/titusRunJobStage';

import { TitusCloneServerGroupModal } from './serverGroup/configure/wizard/TitusCloneServerGroupModal';

import './logo/titus.logo.less';
import { TITUS_SECURITYGROUP_SECURITYGROUP_READ_SERVICE } from './securityGroup/securityGroup.read.service';
import { TITUS_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_TITUS_CONTROLLER } from './serverGroup/details/serverGroupDetails.titus.controller';
import { TITUS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER } from './serverGroup/configure/ServerGroupCommandBuilder';
import { TITUS_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_TITUS_MODULE } from './serverGroup/configure/serverGroup.configure.titus.module';
import { TITUS_SERVERGROUP_SERVERGROUP_TRANSFORMER } from './serverGroup/serverGroup.transformer';
import { TITUS_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER } from './instance/details/instance.details.controller';
import { TITUS_PIPELINE_STAGES_FINDAMI_TITUSFINDAMISTAGE } from './pipeline/stages/findAmi/titusFindAmiStage';
import { TITUS_PIPELINE_STAGES_ENABLEASG_TITUSENABLEASGSTAGE } from './pipeline/stages/enableAsg/titusEnableAsgStage';
import { TITUS_PIPELINE_STAGES_DISABLEASG_TITUSDISABLEASGSTAGE } from './pipeline/stages/disableAsg/titusDisableAsgStage';
import { TITUS_PIPELINE_STAGES_DESTROYASG_TITUSDESTROYASGSTAGE } from './pipeline/stages/destroyAsg/titusDestroyAsgStage';
import { TITUS_PIPELINE_STAGES_RESIZEASG_TITUSRESIZEASGSTAGE } from './pipeline/stages/resizeAsg/titusResizeAsgStage';
import { TITUS_PIPELINE_STAGES_CLONESERVERGROUP_TITUSCLONESERVERGROUPSTAGE } from './pipeline/stages/cloneServerGroup/titusCloneServerGroupStage';
import { TITUS_PIPELINE_STAGES_BAKE_TITUSBAKESTAGE } from './pipeline/stages/bake/titusBakeStage';
import { TITUS_PIPELINE_STAGES_DISABLECLUSTER_TITUSDISABLECLUSTERSTAGE } from './pipeline/stages/disableCluster/titusDisableClusterStage';
import { TITUS_PIPELINE_STAGES_SHRINKCLUSTER_TITUSSHRINKCLUSTERSTAGE } from './pipeline/stages/shrinkCluster/titusShrinkClusterStage';
import { TITUS_PIPELINE_STAGES_SCALEDOWNCLUSTER_TITUSSCALEDOWNCLUSTERSTAGE } from './pipeline/stages/scaleDownCluster/titusScaleDownClusterStage';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const TITUS_MODULE = 'spinnaker.titus';
module(TITUS_MODULE, [
  TITUS_REACT_MODULE,
  TITUS_SECURITYGROUP_SECURITYGROUP_READ_SERVICE,
  TITUS_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_TITUS_CONTROLLER,
  TITUS_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER,
  TITUS_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_TITUS_MODULE,
  TITUS_SERVERGROUP_SERVERGROUP_TRANSFORMER,
  TITUS_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER,
  TITUS_PIPELINE_STAGES_FINDAMI_TITUSFINDAMISTAGE,
  TITUS_PIPELINE_STAGES_ENABLEASG_TITUSENABLEASGSTAGE,
  TITUS_PIPELINE_STAGES_DISABLEASG_TITUSDISABLEASGSTAGE,
  TITUS_PIPELINE_STAGES_DESTROYASG_TITUSDESTROYASGSTAGE,
  TITUS_PIPELINE_STAGES_RESIZEASG_TITUSRESIZEASGSTAGE,
  TITUS_PIPELINE_STAGES_CLONESERVERGROUP_TITUSCLONESERVERGROUPSTAGE,
  TITUS_PIPELINE_STAGES_BAKE_TITUSBAKESTAGE,
  TITUS_PIPELINE_STAGES_DISABLECLUSTER_TITUSDISABLECLUSTERSTAGE,
  TITUS_PIPELINE_STAGES_SHRINKCLUSTER_TITUSSHRINKCLUSTERSTAGE,
  TITUS_PIPELINE_STAGES_SCALEDOWNCLUSTER_TITUSSCALEDOWNCLUSTERSTAGE,
  TITUS_SERVERGROUP_DETAILS_CAPACITYDETAILSSECTION,
  TITUS_SERVERGROUP_DETAILS_LAUNCHCONFIGSECTION,
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
      incompatibleLoadBalancerTypes: [
        {
          type: 'classic',
          reason: 'Classic Load Balancers cannot be used with Titus as they do not have IP based target groups.',
        },
      ],
      useProvider: 'aws',
    },
    instance: {
      detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
      detailsController: 'titusInstanceDetailsCtrl',
    },
  });
});

DeploymentStrategyRegistry.registerProvider('titus', ['custom', 'redblack']);
