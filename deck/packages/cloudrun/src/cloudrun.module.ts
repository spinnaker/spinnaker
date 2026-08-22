import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './help/cloudrun.help';
import { CloudrunInstanceDetails } from './instance/details/CloudrunInstanceDetails';
import { CloudrunLoadBalancerModal } from './loadBalancer/configure/wizard/CloudrunLoadBalancerModal';
import { CloudrunLoadBalancerActions } from './loadBalancer/details/CloudrunLoadBalancerActions';
import {
  CloudrunLoadBalancerDetailsSection,
  CloudrunLoadBalancerDnsSection,
  CloudrunLoadBalancerStatusSection,
  CloudrunLoadBalancerTrafficSplitSection,
} from './loadBalancer/details/sections';
import { useCloudrunLoadBalancerDetails } from './loadBalancer/details/useCloudrunLoadBalancerDetails';
import { CloudrunLoadBalancerTransformer } from './loadBalancer/loadBalancerTransformer';
import logo from './logo/cloudrun.logo.png';
import './pipeline/stages/deployManifest/deployStage';
import './pipeline/stages/editLoadBalancer/cloudrunEditLoadBalancerStage';
import { CloudrunV2ServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { ServerGroupWizard } from './serverGroup/configure/wizard/serverGroupWizard';
import { CloudrunServerGroupActions } from './serverGroup/details/CloudrunServerGroupActions';
import { cloudrunServerGroupDetailsGetter } from './serverGroup/details/cloudrunServerGroupDetailsGetter';
import {
  CloudrunServerGroupHealthSection,
  CloudrunServerGroupInformationSection,
  CloudrunServerGroupSizeSection,
} from './serverGroup/details/sections';
import { CloudrunV2ServerGroupTransformer } from './serverGroup/serverGroupTransformer.service';

import './logo/cloudrun.logo.less';

CloudProviderRegistry.registerProvider('cloudrun', {
  name: 'cloudrun',
  logo: {
    path: logo,
  },

  instance: {
    details: CloudrunInstanceDetails,
  },
  serverGroup: {
    CloneServerGroupModal: ServerGroupWizard,
    commandBuilder: CloudrunV2ServerGroupCommandBuilder,
    detailsActions: CloudrunServerGroupActions,
    detailsGetter: cloudrunServerGroupDetailsGetter,
    detailsSections: [
      CloudrunServerGroupInformationSection,
      CloudrunServerGroupSizeSection,
      CloudrunServerGroupHealthSection,
    ],
    transformer: CloudrunV2ServerGroupTransformer,
    skipUpstreamStageCheck: true,
  },

  loadBalancer: {
    transformer: CloudrunLoadBalancerTransformer,
    CreateLoadBalancerModal: CloudrunLoadBalancerModal,
    useDetailsHook: useCloudrunLoadBalancerDetails,
    detailsActions: CloudrunLoadBalancerActions,
    detailsSections: [
      CloudrunLoadBalancerDetailsSection,
      CloudrunLoadBalancerStatusSection,
      CloudrunLoadBalancerTrafficSplitSection,
      CloudrunLoadBalancerDnsSection,
    ],
  },
});

DeploymentStrategyRegistry.registerProvider('cloudrun', ['custom', 'redblack', 'rollingpush', 'rollingredblack']);
