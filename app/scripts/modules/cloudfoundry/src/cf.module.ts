import { CloudProviderRegistry } from '@spinnaker/core';

import './help/cloudfoundry.help';
import './logo/cf.logo.less';

import {
  CloudFoundryLoadBalancerDetails,
  CloudFoundryLoadBalancerTransformer,
  CloudFoundryNoLoadBalancerModal,
} from 'cloudfoundry/loadBalancer';

import {
  ApplicationManagerSection,
  BoundServicesSection,
  BuildSection,
  cfServerGroupDetailsGetter,
  CloudFoundryCreateServerGroupModal,
  CloudFoundryServerGroupActions,
  CloudFoundryServerGroupCommandBuilderShim,
  CloudFoundryServerGroupTransformer,
  EnvironmentVariablesSection,
  HealthCheckSection,
  MetricsSection,
  PackageSection,
  ServerGroupInformationSection,
  ServerGroupSizingSection,
} from 'cloudfoundry/serverGroup';

import { CloudFoundryInstanceDetails } from 'cloudfoundry/instance/details';

import cloudFoundryLogo from './logo/cf.logo.svg';

import 'cloudfoundry/common/applicationName.validator';
import 'cloudfoundry/pipeline/config/validation/cfTargetImpedance.validator';
import 'cloudfoundry/pipeline/config/validation/instanceSize.validator';
import 'cloudfoundry/pipeline/config/validation/requiredRoutes.validator';
import './pipeline/stages/cloneServerGroup/cloudfoundryCloneServerGroupStage.module';
import './pipeline/stages/createServiceKey/cloudfoundryCreateServiceKeyStage.module';
import './pipeline/stages/deleteServiceKey/cloudfoundryDeleteServiceKeyStage.module';
import './pipeline/stages/deployService/cloudfoundryDeployServiceStage.module';
import './pipeline/stages/destroyAsg/cloudfoundryDestroyAsgStage.module';
import './pipeline/stages/destroyService/cloudfoundryDestroyServiceStage.module';
import './pipeline/stages/disableAsg/cloudfoundryDisableAsgStage.module';
import './pipeline/stages/enableAsg/cloudfoundryEnableAsgStage.module';
import './pipeline/stages/mapLoadBalancers/cloudfoundryMapLoadBalancersStage.module';
import './pipeline/stages/resizeAsg/cloudfoundryResizeAsgStage.module';
import './pipeline/stages/rollbackCluster/cloudfoundryRollbackClusterStage.module';
import './pipeline/stages/shareService/cloudfoundryShareServiceStage.module';
import './pipeline/stages/unmapLoadBalancers/cloudfoundryUnmapLoadBalancersStage.module';
import './pipeline/stages/unshareService/cloudfoundryUnshareServiceStage.module';
import './pipeline/stages/runJob/cloudfoundryRunJob.module';
import './pipeline/stages/bakeCloudFoundryManifest/bakeCloudFoundryManifestStage';
import './pipeline/stages/createServiceBindings/cloudFoundryCreateServiceBindingsStage';

CloudProviderRegistry.registerProvider('cloudfoundry', {
  name: 'Cloud Foundry',
  logo: {
    path: cloudFoundryLogo,
  },
  loadBalancer: {
    transformer: CloudFoundryLoadBalancerTransformer,
    details: CloudFoundryLoadBalancerDetails,
    CreateLoadBalancerModal: CloudFoundryNoLoadBalancerModal,
  },
  serverGroup: {
    skipUpstreamStageCheck: true,
    transformer: CloudFoundryServerGroupTransformer,
    detailsActions: CloudFoundryServerGroupActions,
    detailsGetter: cfServerGroupDetailsGetter,
    detailsSections: [
      ServerGroupInformationSection,
      ApplicationManagerSection,
      MetricsSection,
      ServerGroupSizingSection,
      HealthCheckSection,
      BuildSection,
      PackageSection,
      BoundServicesSection,
      EnvironmentVariablesSection,
    ],
    CloneServerGroupModal: CloudFoundryCreateServerGroupModal,
    commandBuilder: CloudFoundryServerGroupCommandBuilderShim,
    scalingActivitiesEnabled: false, // FIXME enable?
  },
  instance: {
    details: CloudFoundryInstanceDetails,
  },
});
