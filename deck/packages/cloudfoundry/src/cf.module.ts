import { CloudProviderRegistry } from '@spinnaker/core';

import './common/applicationName.validator';
import './help/cloudfoundry.help';
import { CloudFoundryInstanceDetails } from './instance/details';
import {
  CloudFoundryLoadBalancerDetails,
  CloudFoundryLoadBalancerTransformer,
  CloudFoundryMapLoadBalancerModal,
} from './loadBalancer';
import cloudFoundryLogo from './logo/cf.logo.svg';
import './pipeline/config/validation/cfTargetImpedance.validator';
import './pipeline/config/validation/instanceSize.validator';
import './pipeline/config/validation/requiredRoutes.validator';
import './pipeline/stages/bakeCloudFoundryManifest/bakeCloudFoundryManifestStage';
import './pipeline/stages/cloneServerGroup/cloudFoundryCloneServerGroupStage.module';
import './pipeline/stages/createServiceBindings/cloudFoundryCreateServiceBindingsStage';
import './pipeline/stages/createServiceKey/cloudFoundryCreateServiceKeyStage.module';
import './pipeline/stages/deleteServiceBindings/cloudFoundryDeleteServiceBindingsStage';
import './pipeline/stages/deleteServiceKey/cloudFoundryDeleteServiceKeyStage.module';
import './pipeline/stages/deployService/cloudFoundryDeployServiceStage.module';
import './pipeline/stages/destroyAsg/cloudFoundryDestroyAsgStage.module';
import './pipeline/stages/destroyService/cloudFoundryDestroyServiceStage.module';
import './pipeline/stages/disableAsg/cloudFoundryDisableAsgStage.module';
import './pipeline/stages/enableAsg/cloudFoundryEnableAsgStage.module';
import './pipeline/stages/mapLoadBalancers/cloudFoundryMapLoadBalancersStage.module';
import './pipeline/stages/resizeAsg/cloudFoundryResizeAsgStage.module';
import './pipeline/stages/rollbackCluster/cloudFoundryRollbackClusterStage.module';
import './pipeline/stages/runJob/cloudFoundryRunJob.module';
import './pipeline/stages/shareService/cloudFoundryShareServiceStage.module';
import './pipeline/stages/unmapLoadBalancers/cloudFoundryUnmapLoadBalancersStage.module';
import './pipeline/stages/unshareService/cloudFoundryUnshareServiceStage.module';
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
} from './serverGroup';

import './logo/cf.logo.less';

CloudProviderRegistry.registerProvider('cloudfoundry', {
  name: 'Cloud Foundry',
  logo: {
    path: cloudFoundryLogo,
  },
  loadBalancer: {
    transformer: CloudFoundryLoadBalancerTransformer,
    details: CloudFoundryLoadBalancerDetails,
    CreateLoadBalancerModal: CloudFoundryMapLoadBalancerModal,
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
