import { CloudProviderRegistry, SETTINGS } from '@spinnaker/core';

import './help/kubernetes.help';
import { KubernetesInstanceDetails } from './instance';
import {
  KubernetesLoadBalancerActions,
  KubernetesLoadBalancerTransformer,
  LoadBalancerAnnotationCustomSection,
  LoadBalancerEventsSection,
  LoadBalancerInformationSection,
  LoadBalancerLabelsSection,
  LoadBalancerStatusSection,
  useKubernetesLoadBalancerDetails,
} from './loadBalancer';
import kubernetesLogo from './logo/kubernetes.logo.svg';
import { ManifestWizard } from './manifest/wizard/ManifestWizard';
import './pipelines/stages';
import './pipelines/stages/traffic/disableManifest.stage';
import './pipelines/stages/traffic/enableManifest.stage';
import './pipelines/validation/manifestSelector.validator';
import './rawResource';
import './resources/resources.state';
import {
  KubernetesSecurityGroupDetails,
  KubernetesSecurityGroupReader,
  KubernetesV2SecurityGroupTransformer,
} from './securityGroup';
import {
  ServerGroupAnnotationCustomSection,
  ServerGroupEventsSection,
  ServerGroupHealthSection,
  ServerGroupImagesSection,
  ServerGroupInformationSection,
  ServerGroupLabelsSection,
  ServerGroupManifestStatusSection,
  ServerGroupSizeSection,
} from './serverGroup';
import { KubernetesV2ServerGroupCommandBuilder } from './serverGroup';
import { KubernetesV2ServerGroupTransformer } from './serverGroup';
import { KubernetesServerGroupActions } from './serverGroup/details/KubernetesServerGroupActions';
import { kubernetesServerGroupDetailsGetter } from './serverGroup/details/kubernetesServerGroupDetailsGetter';
import { ServerGroupManagerDetails } from './serverGroupManager/details/ServerGroupManagerDetails';
import './validation/applicationName.validator';

import './logo/kubernetes.logo.less';

export function registerKubernetesProvider(): void {
  CloudProviderRegistry.registerProvider('kubernetes', {
    name: 'Kubernetes',
    adHocInfrastructureWritesEnabled: SETTINGS.kubernetesAdHocInfraWritesEnabled,
    logo: {
      path: kubernetesLogo,
    },
    serverGroup: {
      CloneServerGroupModal: ManifestWizard,
      detailsActions: KubernetesServerGroupActions,
      detailsGetter: kubernetesServerGroupDetailsGetter,
      detailsSections: [
        ServerGroupManifestStatusSection,
        ServerGroupInformationSection,
        ServerGroupAnnotationCustomSection,
        ServerGroupImagesSection,
        ServerGroupEventsSection,
        ServerGroupLabelsSection,
        ServerGroupSizeSection,
        ServerGroupHealthSection,
      ],
      commandBuilder: KubernetesV2ServerGroupCommandBuilder,
      transformer: KubernetesV2ServerGroupTransformer,
    },
    serverGroupManager: {
      details: ServerGroupManagerDetails,
    },
    loadBalancer: {
      CreateLoadBalancerModal: ManifestWizard,
      useDetailsHook: useKubernetesLoadBalancerDetails,
      detailsActions: KubernetesLoadBalancerActions,
      detailsSections: [
        LoadBalancerInformationSection,
        LoadBalancerStatusSection,
        LoadBalancerAnnotationCustomSection,
        LoadBalancerEventsSection,
        LoadBalancerLabelsSection,
      ],
      transformer: KubernetesLoadBalancerTransformer,
    },
    securityGroup: {
      reader: KubernetesSecurityGroupReader,
      CreateSecurityGroupModal: ManifestWizard,
      details: KubernetesSecurityGroupDetails,
      transformer: KubernetesV2SecurityGroupTransformer,
    },
    instance: {
      details: KubernetesInstanceDetails,
    },
    unsupportedStageTypes: [
      'deploy',
      'destroyServerGroup',
      'disableCluster',
      'disableServerGroup',
      'enableServerGroup',
      'findImage',
      'resizeServerGroup',
      'rollbackCluster',
      'runJob',
      'scaleDown',
      'scaleDownCluster',
      'shrinkCluster',
      'upsertLoadBalancers',
    ],
  });
}

registerKubernetesProvider();
