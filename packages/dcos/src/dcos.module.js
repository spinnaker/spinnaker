'use strict';

import { module } from 'angular';

import { CloudProviderRegistry } from '@spinnaker/core';

import { DCOS_KEY_VALUE_DETAILS } from './common/keyValueDetails.component';
import { DCOS_COMMON_SELECTFIELD_DIRECTIVE } from './common/selectField.directive';
import './help/dcos.help';
import { DCOS_INSTANCE_DETAILS_DETAILS_DCOS_MODULE } from './instance/details/details.dcos.module';
import { DCOS_LOADBALANCER_CONFIGURE_CONFIGURE_DCOS_MODULE } from './loadBalancer/configure/configure.dcos.module';
import { DCOS_LOADBALANCER_DETAILS_DETAILS_DCOS_MODULE } from './loadBalancer/details/details.dcos.module';
import { DCOS_LOADBALANCER_TRANSFORMER } from './loadBalancer/transformer';
import logo from './logo/dcos.logo.png';
import { DCOS_PIPELINE_STAGES_DESTROYASG_DCOSDESTROYASGSTAGE } from './pipeline/stages/destroyAsg/dcosDestroyAsgStage';
import { DCOS_PIPELINE_STAGES_DISABLEASG_DCOSDISABLEASGSTAGE } from './pipeline/stages/disableAsg/dcosDisableAsgStage';
import { DCOS_PIPELINE_STAGES_DISABLECLUSTER_DCOSDISABLECLUSTERSTAGE } from './pipeline/stages/disableCluster/dcosDisableClusterStage';
import { DCOS_PIPELINE_STAGES_FINDAMI_DCOSFINDAMISTAGE } from './pipeline/stages/findAmi/dcosFindAmiStage';
import { DCOS_PIPELINE_STAGES_RESIZEASG_DCOSRESIZEASGSTAGE } from './pipeline/stages/resizeAsg/dcosResizeAsgStage';
import { DCOS_PIPELINE_STAGES_RUNJOB_RUNJOBSTAGE } from './pipeline/stages/runJob/runJobStage';
import { DCOS_PIPELINE_STAGES_SCALEDOWNCLUSTER_DCOSSCALEDOWNCLUSTERSTAGE } from './pipeline/stages/scaleDownCluster/dcosScaleDownClusterStage';
import { DCOS_PIPELINE_STAGES_SHRINKCLUSTER_DCOSSHRINKCLUSTERSTAGE } from './pipeline/stages/shrinkCluster/dcosShrinkClusterStage';
import { DCOS_PROXY_UI_SERVICE } from './proxy/ui.service';
import { DCOS_SERVERGROUP_CONFIGURE_COMMANDBUILDER } from './serverGroup/configure/CommandBuilder';
import { DCOS_SERVERGROUP_CONFIGURE_CONFIGURE_DCOS_MODULE } from './serverGroup/configure/configure.dcos.module';
import { DCOS_SERVERGROUP_DETAILS_DETAILS_DCOS_MODULE } from './serverGroup/details/details.dcos.module';
import { DCOS_SERVERGROUP_TRANSFORMER } from './serverGroup/transformer';
import { DCOS_VALIDATION_APPLICATIONNAME_VALIDATOR } from './validation/applicationName.validator';

import './logo/dcos.logo.less';

export const DCOS_DCOS_MODULE = 'spinnaker.dcos';
export const name = DCOS_DCOS_MODULE; // for backwards compatibility
module(DCOS_DCOS_MODULE, [
  DCOS_KEY_VALUE_DETAILS,
  DCOS_INSTANCE_DETAILS_DETAILS_DCOS_MODULE,
  DCOS_LOADBALANCER_CONFIGURE_CONFIGURE_DCOS_MODULE,
  DCOS_LOADBALANCER_DETAILS_DETAILS_DCOS_MODULE,
  DCOS_LOADBALANCER_TRANSFORMER,
  DCOS_PIPELINE_STAGES_DESTROYASG_DCOSDESTROYASGSTAGE,
  DCOS_PIPELINE_STAGES_DISABLEASG_DCOSDISABLEASGSTAGE,
  DCOS_PIPELINE_STAGES_DISABLECLUSTER_DCOSDISABLECLUSTERSTAGE,
  DCOS_PIPELINE_STAGES_FINDAMI_DCOSFINDAMISTAGE,
  DCOS_PIPELINE_STAGES_RESIZEASG_DCOSRESIZEASGSTAGE,
  DCOS_PIPELINE_STAGES_RUNJOB_RUNJOBSTAGE,
  DCOS_PIPELINE_STAGES_SCALEDOWNCLUSTER_DCOSSCALEDOWNCLUSTERSTAGE,
  DCOS_PIPELINE_STAGES_SHRINKCLUSTER_DCOSSHRINKCLUSTERSTAGE,
  DCOS_PROXY_UI_SERVICE,
  DCOS_SERVERGROUP_CONFIGURE_COMMANDBUILDER,
  DCOS_SERVERGROUP_CONFIGURE_CONFIGURE_DCOS_MODULE,
  DCOS_SERVERGROUP_DETAILS_DETAILS_DCOS_MODULE,
  DCOS_SERVERGROUP_TRANSFORMER,
  DCOS_VALIDATION_APPLICATIONNAME_VALIDATOR,
  DCOS_COMMON_SELECTFIELD_DIRECTIVE,
]).config(function () {
  CloudProviderRegistry.registerProvider('dcos', {
    name: 'DC/OS',
    logo: {
      path: logo,
    },
    instance: {
      detailsTemplateUrl: require('./instance/details/details.html'),
      detailsController: 'dcosInstanceDetailsController',
    },
    loadBalancer: {
      transformer: 'dcosLoadBalancerTransformer',
      detailsTemplateUrl: require('./loadBalancer/details/details.html'),
      detailsController: 'dcosLoadBalancerDetailsController',
      createLoadBalancerTemplateUrl: require('./loadBalancer/configure/wizard/createWizard.html'),
      createLoadBalancerController: 'dcosUpsertLoadBalancerController',
    },
    image: {
      reader: 'dcosImageReader',
    },
    serverGroup: {
      skipUpstreamStageCheck: true,
      transformer: 'dcosServerGroupTransformer',
      detailsTemplateUrl: require('./serverGroup/details/details.html'),
      detailsController: 'dcosServerGroupDetailsController',
      cloneServerGroupController: 'dcosCloneServerGroupController',
      cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/wizard.html'),
      commandBuilder: 'dcosServerGroupCommandBuilder',
      configurationService: 'dcosServerGroupConfigurationService',
    },
  });
});
