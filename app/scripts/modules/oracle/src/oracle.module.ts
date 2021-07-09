import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './helpContents/oracleHelpContents';
import { ORACLE_IMAGE_IMAGE_READER } from './image/image.reader';
import { ORACLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER } from './instance/details/instance.details.controller';
import { ORACLE_LOAD_BALANCER_CREATE_CONTROLLER } from './loadBalancer/configure/createLoadBalancer.controller';
import { ORACLE_LOAD_BALANCER_DETAIL_CONTROLLER } from './loadBalancer/details/loadBalancerDetail.controller';
import { ORACLE_LOAD_BALANCER_TRANSFORMER } from './loadBalancer/loadBalancer.transformer';
import { ORACLE_PIPELINE_STAGES_BAKE_OCIBAKESTAGE } from './pipeline/stages/bake/ociBakeStage';
import { ORACLE_PIPELINE_STAGES_DESTROYASG_DESTROYASGSTAGE } from './pipeline/stages/destroyAsg/destroyAsgStage';
import { ORACLE_PIPELINE_STAGES_DISABLEASG_DISABLEASGSTAGE } from './pipeline/stages/disableAsg/disableAsgStage';
import { ORACLE_PIPELINE_STAGES_FINDAMI_FINDAMISTAGE } from './pipeline/stages/findAmi/findAmiStage';
import { ORACLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_ORACLEFINDIMAGEFROMTAGSSTAGE } from './pipeline/stages/findImageFromTags/oracleFindImageFromTagsStage';
import { ORACLE_PIPELINE_STAGES_RESIZEASG_RESIZEASGSTAGE } from './pipeline/stages/resizeAsg/resizeAsgStage';
import { ORACLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_SCALEDOWNCLUSTERSTAGE } from './pipeline/stages/scaleDownCluster/scaleDownClusterStage';
import { ORACLE_PIPELINE_STAGES_SHRINKCLUSTER_SHRINKCLUSTERSTAGE } from './pipeline/stages/shrinkCluster/shrinkClusterStage';
import { ORACLE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUP_CONTROLLER } from './securityGroup/configure/createSecurityGroup.controller';
import { ORACLE_SECURITYGROUP_SECURITYGROUP_READER } from './securityGroup/securityGroup.reader';
import { ORACLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER } from './securityGroup/securityGroup.transformer';
import { ORACLE_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_MODULE } from './serverGroup/configure/serverGroup.configure.module';
import { ORACLE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { ORACLE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_CONTROLLER } from './serverGroup/configure/wizard/cloneServerGroup.controller';
import { ORACLE_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_CONTROLLER } from './serverGroup/details/serverGroupDetails.controller';
import { ORACLE_SERVERGROUP_SERVERGROUP_TRANSFORMER } from './serverGroup/serverGroup.transformer';

export const ORACLE_MODULE = 'spinnaker.oracle';
module(ORACLE_MODULE, [
  // Pipeline
  ORACLE_PIPELINE_STAGES_BAKE_OCIBAKESTAGE,
  ORACLE_PIPELINE_STAGES_DESTROYASG_DESTROYASGSTAGE,
  ORACLE_PIPELINE_STAGES_DISABLEASG_DISABLEASGSTAGE,
  ORACLE_PIPELINE_STAGES_FINDAMI_FINDAMISTAGE,
  ORACLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_ORACLEFINDIMAGEFROMTAGSSTAGE,
  ORACLE_PIPELINE_STAGES_RESIZEASG_RESIZEASGSTAGE,
  ORACLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_SCALEDOWNCLUSTERSTAGE,
  ORACLE_PIPELINE_STAGES_SHRINKCLUSTER_SHRINKCLUSTERSTAGE,

  // Load Balancers
  ORACLE_LOAD_BALANCER_TRANSFORMER,
  ORACLE_LOAD_BALANCER_DETAIL_CONTROLLER,
  ORACLE_LOAD_BALANCER_CREATE_CONTROLLER,

  // Server Groups
  ORACLE_SERVERGROUP_SERVERGROUP_TRANSFORMER,
  ORACLE_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_MODULE,
  ORACLE_SERVERGROUP_DETAILS_SERVERGROUPDETAILS_CONTROLLER,
  ORACLE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE,
  ORACLE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_CONTROLLER,
  // Images
  ORACLE_IMAGE_IMAGE_READER,
  // Instances
  ORACLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER,
  // Firewalls
  ORACLE_SECURITYGROUP_SECURITYGROUP_READER,
  ORACLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER,
  ORACLE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUP_CONTROLLER,
]).config(function () {
  CloudProviderRegistry.registerProvider('oracle', {
    name: 'Oracle',
    image: {
      reader: 'oracleImageReader',
    },
    loadBalancer: {
      transformer: 'oracleLoadBalancerTransformer',
      detailsTemplateUrl: require('./loadBalancer/details/loadBalancerDetail.html'),
      detailsController: 'oracleLoadBalancerDetailCtrl',
      createLoadBalancerTemplateUrl: require('./loadBalancer/configure/createLoadBalancer.html'),
      createLoadBalancerController: 'oracleCreateLoadBalancerCtrl',
    },
    serverGroup: {
      transformer: 'oracleServerGroupTransformer',
      detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
      detailsController: 'oracleServerGroupDetailsCtrl',
      commandBuilder: 'oracleServerGroupCommandBuilder',
      cloneServerGroupController: 'oracleCloneServerGroupCtrl',
      cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
      configurationService: 'oracleServerGroupConfigurationService',
    },
    instance: {
      detailsController: 'oracleInstanceDetailsCtrl',
      detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
    },
    securityGroup: {
      reader: 'oracleSecurityGroupReader',
      transformer: 'oracleSecurityGroupTransformer',
      createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
      createSecurityGroupController: 'oracleCreateSecurityGroupCtrl',
    },
  });
});

DeploymentStrategyRegistry.registerProvider('oracle', []);
