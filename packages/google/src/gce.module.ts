import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { GCE_PREDICTIVE_AUTOSCALING } from './autoscalingPolicy/components/metricSettings/GcePredictiveAutoscaling';
import { GOOGLE_CACHE_CACHECONFIGURER_SERVICE } from './cache/cacheConfigurer.service';
import { GOOGLE_COMMON_XPNNAMING_GCE_SERVICE } from './common/xpnNaming.gce.service';
import './help/gce.help';
import { GceImageReader } from './image';
import { GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCE_FILTER } from './instance/custom/customInstance.filter';
import { GOOGLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER } from './instance/details/instance.details.controller';
import { GOOGLE_INSTANCE_GCEINSTANCETYPE_SERVICE } from './instance/gceInstanceType.service';
import { GOOGLE_INSTANCE_GCEMULTIINSTANCETASK_TRANSFORMER } from './instance/gceMultiInstanceTask.transformer';
import { IAP_INTERCEPTOR } from './interceptors/iap.interceptor';
import { GCE_LOAD_BALANCER_CHOICE_MODAL } from './loadBalancer/configure/choice/gceLoadBalancerChoice.modal';
import { GOOGLE_LOADBALANCER_CONFIGURE_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER } from './loadBalancer/configure/http/createHttpLoadBalancer.controller';
import { GCE_INTERNAL_LOAD_BALANCER_CTRL } from './loadBalancer/configure/internal/gceCreateInternalLoadBalancer.controller';
import { GOOGLE_LOADBALANCER_CONFIGURE_INTERNAL_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER } from './loadBalancer/configure/internalhttp/createInternalHttpLoadBalancer.controller';
import { GOOGLE_LOADBALANCER_CONFIGURE_NETWORK_CREATELOADBALANCER_CONTROLLER } from './loadBalancer/configure/network/createLoadBalancer.controller';
import { GCE_SSL_LOAD_BALANCER_CTRL } from './loadBalancer/configure/ssl/gceCreateSslLoadBalancer.controller';
import { GCE_TCP_LOAD_BALANCER_CTRL } from './loadBalancer/configure/tcp/gceCreateTcpLoadBalancer.controller';
import { GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERDETAIL_CONTROLLER } from './loadBalancer/details/loadBalancerDetail.controller';
import { LOAD_BALANCER_SET_TRANSFORMER } from './loadBalancer/loadBalancer.setTransformer';
import { GOOGLE_LOADBALANCER_LOADBALANCER_TRANSFORMER } from './loadBalancer/loadBalancer.transformer';
import logo from './logo/gce.logo.png';
import { GOOGLE_PIPELINE_STAGES_BAKE_GCEBAKESTAGE } from './pipeline/stages/bake/gceBakeStage';
import { GOOGLE_PIPELINE_STAGES_CLONESERVERGROUP_GCECLONESERVERGROUPSTAGE } from './pipeline/stages/cloneServerGroup/gceCloneServerGroupStage';
import { GOOGLE_PIPELINE_STAGES_DESTROYASG_GCEDESTROYASGSTAGE } from './pipeline/stages/destroyAsg/gceDestroyAsgStage';
import { GOOGLE_PIPELINE_STAGES_DISABLEASG_GCEDISABLEASGSTAGE } from './pipeline/stages/disableAsg/gceDisableAsgStage';
import { GOOGLE_PIPELINE_STAGES_DISABLECLUSTER_GCEDISABLECLUSTERSTAGE } from './pipeline/stages/disableCluster/gceDisableClusterStage';
import { GOOGLE_PIPELINE_STAGES_ENABLEASG_GCEENABLEASGSTAGE } from './pipeline/stages/enableAsg/gceEnableAsgStage';
import { GOOGLE_PIPELINE_STAGES_FINDAMI_GCEFINDAMISTAGE } from './pipeline/stages/findAmi/gceFindAmiStage';
import { GOOGLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_GCEFINDIMAGEFROMTAGSSTAGE } from './pipeline/stages/findImageFromTags/gceFindImageFromTagsStage';
import { GOOGLE_PIPELINE_STAGES_RESIZEASG_GCERESIZEASGSTAGE } from './pipeline/stages/resizeAsg/gceResizeAsgStage';
import { GOOGLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_GCESCALEDOWNCLUSTERSTAGE } from './pipeline/stages/scaleDownCluster/gceScaleDownClusterStage';
import { GOOGLE_PIPELINE_STAGES_SHRINKCLUSTER_GCESHRINKCLUSTERSTAGE } from './pipeline/stages/shrinkCluster/gceShrinkClusterStage';
import { GOOGLE_PIPELINE_STAGES_TAGIMAGE_GCETAGIMAGESTAGE } from './pipeline/stages/tagImage/gceTagImageStage';
import { GOOGLE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUP_CONTROLLER } from './securityGroup/configure/createSecurityGroup.controller';
import { GOOGLE_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUP_CONTROLLER } from './securityGroup/configure/editSecurityGroup.controller';
import { GOOGLE_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER } from './securityGroup/details/securityGroupDetail.controller';
import { GOOGLE_SECURITYGROUP_SECURITYGROUP_READER } from './securityGroup/securityGroup.reader';
import { GOOGLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER } from './securityGroup/securityGroup.transformer';
import { GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_GCE_MODULE } from './serverGroup/configure/serverGroup.configure.gce.module';
import { GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_GCE_CONTROLLER } from './serverGroup/configure/wizard/cloneServerGroup.gce.controller';
import { GCE_SERVER_GROUP_DISK_DESCRIPTIONS } from './serverGroup/details/ServerGroupDiskDescriptions';
import { GCE_SCALE_IN_CONTROLS } from './serverGroup/details/autoscalingPolicy/modal/GceScaleInControls';
import { GOOGLE_SERVERGROUP_DETAILS_SERVERGROUP_DETAILS_GCE_MODULE } from './serverGroup/details/serverGroup.details.gce.module';
import { GOOGLE_SERVERGROUP_SERVERGROUP_TRANSFORMER } from './serverGroup/serverGroup.transformer';
import { GOOGLE_SUBNET_SUBNET_RENDERER } from './subnet/subnet.renderer';
import { GOOGLE_VALIDATION_APPLICATIONNAME_VALIDATOR } from './validation/applicationName.validator';

import './logo/gce.logo.less';

export const GOOGLE_MODULE = 'spinnaker.gce';
module(GOOGLE_MODULE, [
  LOAD_BALANCER_SET_TRANSFORMER,
  GCE_INTERNAL_LOAD_BALANCER_CTRL,
  GCE_LOAD_BALANCER_CHOICE_MODAL,
  GCE_SSL_LOAD_BALANCER_CTRL,
  GCE_TCP_LOAD_BALANCER_CTRL,
  IAP_INTERCEPTOR,
  GCE_SERVER_GROUP_DISK_DESCRIPTIONS,
  GCE_SCALE_IN_CONTROLS,
  GCE_PREDICTIVE_AUTOSCALING,
  GOOGLE_SERVERGROUP_DETAILS_SERVERGROUP_DETAILS_GCE_MODULE,
  GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUPCOMMANDBUILDER_SERVICE,
  GOOGLE_SERVERGROUP_CONFIGURE_WIZARD_CLONESERVERGROUP_GCE_CONTROLLER,
  GOOGLE_SERVERGROUP_CONFIGURE_SERVERGROUP_CONFIGURE_GCE_MODULE,
  GOOGLE_SERVERGROUP_SERVERGROUP_TRANSFORMER,
  GOOGLE_PIPELINE_STAGES_BAKE_GCEBAKESTAGE,
  GOOGLE_PIPELINE_STAGES_CLONESERVERGROUP_GCECLONESERVERGROUPSTAGE,
  GOOGLE_PIPELINE_STAGES_DESTROYASG_GCEDESTROYASGSTAGE,
  GOOGLE_PIPELINE_STAGES_DISABLEASG_GCEDISABLEASGSTAGE,
  GOOGLE_PIPELINE_STAGES_DISABLECLUSTER_GCEDISABLECLUSTERSTAGE,
  GOOGLE_PIPELINE_STAGES_ENABLEASG_GCEENABLEASGSTAGE,
  GOOGLE_PIPELINE_STAGES_FINDAMI_GCEFINDAMISTAGE,
  GOOGLE_PIPELINE_STAGES_FINDIMAGEFROMTAGS_GCEFINDIMAGEFROMTAGSSTAGE,
  GOOGLE_PIPELINE_STAGES_RESIZEASG_GCERESIZEASGSTAGE,
  GOOGLE_PIPELINE_STAGES_SCALEDOWNCLUSTER_GCESCALEDOWNCLUSTERSTAGE,
  GOOGLE_PIPELINE_STAGES_SHRINKCLUSTER_GCESHRINKCLUSTERSTAGE,
  GOOGLE_PIPELINE_STAGES_TAGIMAGE_GCETAGIMAGESTAGE,
  GOOGLE_INSTANCE_GCEINSTANCETYPE_SERVICE,
  GOOGLE_INSTANCE_GCEMULTIINSTANCETASK_TRANSFORMER,
  GOOGLE_INSTANCE_CUSTOM_CUSTOMINSTANCE_FILTER,
  GOOGLE_LOADBALANCER_LOADBALANCER_TRANSFORMER,
  GOOGLE_LOADBALANCER_DETAILS_LOADBALANCERDETAIL_CONTROLLER,
  GOOGLE_LOADBALANCER_CONFIGURE_NETWORK_CREATELOADBALANCER_CONTROLLER,
  GOOGLE_LOADBALANCER_CONFIGURE_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER,
  GOOGLE_LOADBALANCER_CONFIGURE_INTERNAL_HTTP_CREATEHTTPLOADBALANCER_CONTROLLER,
  GOOGLE_INSTANCE_DETAILS_INSTANCE_DETAILS_CONTROLLER,
  GOOGLE_SECURITYGROUP_DETAILS_SECURITYGROUPDETAIL_CONTROLLER,
  GOOGLE_SECURITYGROUP_CONFIGURE_CREATESECURITYGROUP_CONTROLLER,
  GOOGLE_SECURITYGROUP_CONFIGURE_EDITSECURITYGROUP_CONTROLLER,
  GOOGLE_SECURITYGROUP_SECURITYGROUP_TRANSFORMER,
  GOOGLE_SECURITYGROUP_SECURITYGROUP_READER,
  GOOGLE_SUBNET_SUBNET_RENDERER,
  GOOGLE_VALIDATION_APPLICATIONNAME_VALIDATOR,
  GOOGLE_CACHE_CACHECONFIGURER_SERVICE,
  GOOGLE_COMMON_XPNNAMING_GCE_SERVICE,
]).config(() => {
  CloudProviderRegistry.registerProvider('gce', {
    name: 'Google',
    logo: {
      path: logo,
    },
    cache: {
      configurer: 'gceCacheConfigurer',
    },
    image: {
      reader: GceImageReader,
    },
    serverGroup: {
      transformer: 'gceServerGroupTransformer',
      detailsTemplateUrl: require('./serverGroup/details/serverGroupDetails.html'),
      detailsController: 'gceServerGroupDetailsCtrl',
      cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/serverGroupWizard.html'),
      cloneServerGroupController: 'gceCloneServerGroupCtrl',
      commandBuilder: 'gceServerGroupCommandBuilder',
      configurationService: 'gceServerGroupConfigurationService',
    },
    instance: {
      instanceTypeService: 'gceInstanceTypeService',
      detailsTemplateUrl: require('./instance/details/instanceDetails.html'),
      detailsController: 'gceInstanceDetailsCtrl',
      multiInstanceTaskTransformer: 'gceMultiInstanceTaskTransformer',
      customInstanceBuilderTemplateUrl: require('./serverGroup/configure/wizard/customInstance/customInstanceBuilder.html'),
    },
    loadBalancer: {
      transformer: 'gceLoadBalancerTransformer',
      setTransformer: 'gceLoadBalancerSetTransformer',
      detailsTemplateUrl: require('./loadBalancer/details/loadBalancerDetails.html'),
      detailsController: 'gceLoadBalancerDetailsCtrl',
      createLoadBalancerTemplateUrl: require('./loadBalancer/configure/choice/gceLoadBalancerChoice.modal.html'),
      createLoadBalancerController: 'gceLoadBalancerChoiceCtrl',
    },
    securityGroup: {
      transformer: 'gceSecurityGroupTransformer',
      reader: 'gceSecurityGroupReader',
      detailsTemplateUrl: require('./securityGroup/details/securityGroupDetail.html'),
      detailsController: 'gceSecurityGroupDetailsCtrl',
      createSecurityGroupTemplateUrl: require('./securityGroup/configure/createSecurityGroup.html'),
      createSecurityGroupController: 'gceCreateSecurityGroupCtrl',
    },
    subnet: {
      renderer: 'gceSubnetRenderer',
    },
    snapshotsEnabled: true,
    applicationProviderFields: {
      templateUrl: require('./applicationProviderFields/gceFields.html'),
    },
  });
});

DeploymentStrategyRegistry.registerProvider('gce', ['custom', 'redblack']);
