import { module } from 'angular';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import { KUBERNETES_KEY_VALUE_DETAILS } from './common/keyValueDetails.component';
import { KUBERNETES_TOLERATIONS } from './common/tolerations/tolerations.component';
import { KUBERNETES_SECURITY_CONTEXT_SELECTOR } from './container/securityContext/securityContextSelector.component';
import { KUBERNETES_SERVERGROUP_ARTIFACTEXTRACTOR } from './serverGroup/artifactExtractor';
import { KubernetesProviderSettings } from '../kubernetes.settings';
import { KubernetesSecurityGroupReader } from 'kubernetes/shared/securityGroup/securityGroup.reader';

import 'kubernetes/shared/validation/applicationName.validator';
import 'kubernetes/shared/help/kubernetes.help';
import 'kubernetes/shared/logo/kubernetes.logo.less';
import { KUBERNETES_V1_AUTOSCALER_AUTOSCALER_WRITE_SERVICE } from './autoscaler/autoscaler.write.service';
import { KUBERNETES_V1_CLUSTER_CLUSTER_KUBERNETES_MODULE } from './cluster/cluster.kubernetes.module';
import { KUBERNETES_V1_CONTAINER_CONFIGURER_DIRECTIVE } from './container/configurer.directive';
import { KUBERNETES_V1_CONTAINER_PROBE_DIRECTIVE } from './container/probe.directive';
import { KUBERNETES_V1_EVENT_EVENT_DIRECTIVE } from './event/event.directive';
import { KUBERNETES_V1_INSTANCE_DETAILS_DETAILS_KUBERNETES_MODULE } from './instance/details/details.kubernetes.module';
import { KUBERNETES_V1_LOADBALANCER_CONFIGURE_CONFIGURE_KUBERNETES_MODULE } from './loadBalancer/configure/configure.kubernetes.module';
import { KUBERNETES_V1_LOADBALANCER_DETAILS_DETAILS_KUBERNETES_MODULE } from './loadBalancer/details/details.kubernetes.module';
import { KUBERNETES_V1_LOADBALANCER_TRANSFORMER } from './loadBalancer/transformer';
import { KUBERNETES_V1_NAMESPACE_MULTISELECTFIELD_COMPONENT } from './namespace/multiSelectField.component';
import { KUBERNETES_V1_NAMESPACE_SELECTFIELD_DIRECTIVE } from './namespace/selectField.directive';
import { KUBERNETES_V1_PIPELINE_STAGES_DESTROYASG_KUBERNETESDESTROYASGSTAGE } from './pipeline/stages/destroyAsg/kubernetesDestroyAsgStage';
import { KUBERNETES_V1_PIPELINE_STAGES_DISABLEASG_KUBERNETESDISABLEASGSTAGE } from './pipeline/stages/disableAsg/kubernetesDisableAsgStage';
import { KUBERNETES_V1_PIPELINE_STAGES_DISABLECLUSTER_KUBERNETESDISABLECLUSTERSTAGE } from './pipeline/stages/disableCluster/kubernetesDisableClusterStage';
import { KUBERNETES_V1_PIPELINE_STAGES_ENABLEASG_KUBERNETESENABLEASGSTAGE } from './pipeline/stages/enableAsg/kubernetesEnableAsgStage';
import { KUBERNETES_V1_PIPELINE_STAGES_FINDAMI_KUBERNETESFINDAMISTAGE } from './pipeline/stages/findAmi/kubernetesFindAmiStage';
import { KUBERNETES_V1_PIPELINE_STAGES_RESIZEASG_RESIZESTAGE } from './pipeline/stages/resizeAsg/resizeStage';
import { KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_RUNJOBSTAGE } from './pipeline/stages/runJob/runJobStage';
import { KUBERNETES_V1_PIPELINE_STAGES_SCALEDOWNCLUSTER_SCALEDOWNCLUSTERSTAGE } from './pipeline/stages/scaleDownCluster/scaleDownClusterStage';
import { KUBERNETES_V1_PIPELINE_STAGES_SHRINKCLUSTER_SHRINKCLUSTERSTAGE } from './pipeline/stages/shrinkCluster/shrinkClusterStage';
import { KUBERNETES_V1_PROXY_UI_SERVICE } from './proxy/ui.service';
import { KUBERNETES_V1_SEARCH_RESULTFORMATTER } from './search/resultFormatter';
import { KUBERNETES_V1_SECURITYGROUP_CONFIGURE_CONFIGURE_KUBERNETES_MODULE } from './securityGroup/configure/configure.kubernetes.module';
import { KUBERNETES_V1_SECURITYGROUP_DETAILS_DETAILS_KUBERNETES_MODULE } from './securityGroup/details/details.kubernetes.module';
import { KUBERNETES_V1_SECURITYGROUP_TRANSFORMER } from './securityGroup/transformer';
import { KUBERNETES_V1_SERVERGROUP_CONFIGURE_COMMANDBUILDER } from './serverGroup/configure/CommandBuilder';
import { KUBERNETES_V1_SERVERGROUP_CONFIGURE_CONFIGURE_KUBERNETES_MODULE } from './serverGroup/configure/configure.kubernetes.module';
import { KUBERNETES_V1_SERVERGROUP_DETAILS_DETAILS_KUBERNETES_MODULE } from './serverGroup/details/details.kubernetes.module';
import { KUBERNETES_V1_SERVERGROUP_PARAMSMIXIN } from './serverGroup/paramsMixin';
import { KUBERNETES_V1_SERVERGROUP_TRANSFORMER } from './serverGroup/transformer';

// load all templates into the $templateCache
const templates = require.context('kubernetes', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const KUBERNETES_V1_MODULE = 'spinnaker.kubernetes.v1';
module(KUBERNETES_V1_MODULE, [
  KUBERNETES_V1_AUTOSCALER_AUTOSCALER_WRITE_SERVICE,
  KUBERNETES_V1_CLUSTER_CLUSTER_KUBERNETES_MODULE,
  KUBERNETES_V1_CONTAINER_CONFIGURER_DIRECTIVE,
  KUBERNETES_V1_CONTAINER_PROBE_DIRECTIVE,
  KUBERNETES_V1_EVENT_EVENT_DIRECTIVE,
  KUBERNETES_V1_INSTANCE_DETAILS_DETAILS_KUBERNETES_MODULE,
  KUBERNETES_KEY_VALUE_DETAILS,
  KUBERNETES_SECURITY_CONTEXT_SELECTOR,
  KUBERNETES_V1_LOADBALANCER_CONFIGURE_CONFIGURE_KUBERNETES_MODULE,
  KUBERNETES_V1_LOADBALANCER_DETAILS_DETAILS_KUBERNETES_MODULE,
  KUBERNETES_V1_LOADBALANCER_TRANSFORMER,
  KUBERNETES_V1_NAMESPACE_MULTISELECTFIELD_COMPONENT,
  KUBERNETES_V1_NAMESPACE_SELECTFIELD_DIRECTIVE,
  KUBERNETES_V1_PIPELINE_STAGES_DESTROYASG_KUBERNETESDESTROYASGSTAGE,
  KUBERNETES_V1_PIPELINE_STAGES_DISABLEASG_KUBERNETESDISABLEASGSTAGE,
  KUBERNETES_V1_PIPELINE_STAGES_DISABLECLUSTER_KUBERNETESDISABLECLUSTERSTAGE,
  KUBERNETES_V1_PIPELINE_STAGES_ENABLEASG_KUBERNETESENABLEASGSTAGE,
  KUBERNETES_V1_PIPELINE_STAGES_FINDAMI_KUBERNETESFINDAMISTAGE,
  KUBERNETES_V1_PIPELINE_STAGES_RESIZEASG_RESIZESTAGE,
  KUBERNETES_V1_PIPELINE_STAGES_RUNJOB_RUNJOBSTAGE,
  KUBERNETES_V1_PIPELINE_STAGES_SCALEDOWNCLUSTER_SCALEDOWNCLUSTERSTAGE,
  KUBERNETES_V1_PIPELINE_STAGES_SHRINKCLUSTER_SHRINKCLUSTERSTAGE,
  KUBERNETES_V1_PROXY_UI_SERVICE,
  KUBERNETES_V1_SEARCH_RESULTFORMATTER,
  KUBERNETES_V1_SECURITYGROUP_CONFIGURE_CONFIGURE_KUBERNETES_MODULE,
  KUBERNETES_V1_SECURITYGROUP_DETAILS_DETAILS_KUBERNETES_MODULE,
  KUBERNETES_V1_SECURITYGROUP_TRANSFORMER,
  KUBERNETES_V1_SERVERGROUP_CONFIGURE_COMMANDBUILDER,
  KUBERNETES_V1_SERVERGROUP_CONFIGURE_CONFIGURE_KUBERNETES_MODULE,
  KUBERNETES_V1_SERVERGROUP_DETAILS_DETAILS_KUBERNETES_MODULE,
  KUBERNETES_SERVERGROUP_ARTIFACTEXTRACTOR,
  KUBERNETES_V1_SERVERGROUP_PARAMSMIXIN,
  KUBERNETES_V1_SERVERGROUP_TRANSFORMER,
  KUBERNETES_TOLERATIONS,
]).config(() => {
  CloudProviderRegistry.registerProvider('kubernetes', {
    name: 'Kubernetes',
    skin: 'v1',
    defaultSkin: true,
    search: {
      resultFormatter: 'kubernetesSearchResultFormatter',
    },
    logo: {
      path: require('../shared/logo/kubernetes.logo.svg'),
    },
    image: {
      reader: 'kubernetesImageReader',
    },
    instance: {
      detailsTemplateUrl: require('./instance/details/details.html'),
      detailsController: 'kubernetesInstanceDetailsController',
    },
    loadBalancer: {
      transformer: 'kubernetesLoadBalancerTransformer',
      detailsTemplateUrl: require('./loadBalancer/details/details.html'),
      detailsController: 'kubernetesLoadBalancerDetailsController',
      createLoadBalancerTemplateUrl: require('./loadBalancer/configure/wizard/createWizard.html'),
      createLoadBalancerController: 'kubernetesUpsertLoadBalancerController',
    },
    securityGroup: {
      reader: KubernetesSecurityGroupReader,
      transformer: 'kubernetesSecurityGroupTransformer',
      detailsTemplateUrl: require('./securityGroup/details/details.html'),
      detailsController: 'kubernetesSecurityGroupDetailsController',
      createSecurityGroupTemplateUrl: require('./securityGroup/configure/wizard/createWizard.html'),
      createSecurityGroupController: 'kubernetesUpsertSecurityGroupController',
    },
    serverGroup: {
      artifactExtractor: 'kubernetesServerGroupArtifactExtractor',
      skipUpstreamStageCheck: true,
      transformer: 'kubernetesServerGroupTransformer',
      detailsTemplateUrl: require('./serverGroup/details/details.html'),
      detailsController: 'kubernetesServerGroupDetailsController',
      cloneServerGroupController: 'kubernetesCloneServerGroupController',
      cloneServerGroupTemplateUrl: require('./serverGroup/configure/wizard/wizard.html'),
      commandBuilder: 'kubernetesServerGroupCommandBuilder',
      configurationService: 'kubernetesServerGroupConfigurationService',
      paramsMixin: 'kubernetesServerGroupParamsMixin',
    },
    unsupportedStageTypes: [
      'scaleManifest',
      'deployManifest',
      'deleteManifest',
      'undoRolloutManifest',
      'findArtifactsFromResource',
      'bakeManifest',
      'patchManifest',
    ],
  });
});

const strategies = ['custom', 'redblack'];
if (KubernetesProviderSettings.defaults.rrb) {
  strategies.push('rollingredblack');
}

DeploymentStrategyRegistry.registerProvider('kubernetes', strategies);
