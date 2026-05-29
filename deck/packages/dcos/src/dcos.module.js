import { CloudProviderRegistry } from '@spinnaker/core';

import './help/dcos.help';
import { dcosImageReader } from './image/image.reader';
import { DcosInstanceDetails } from './instance/details/DcosInstanceDetails';
import {
  DcosCreateLoadBalancerModal,
  DcosLoadBalancerActions,
  dcosLoadBalancerDetailsSections,
  useDcosLoadBalancerDetails,
} from './loadBalancer/details/dcosLoadBalancerDetails';
import { dcosLoadBalancerTransformer } from './loadBalancer/transformer';
import logo from './logo/dcos.logo.png';
import './pipeline/stages/dcosStages';
import { dcosServerGroupCommandBuilder } from './serverGroup/configure/CommandBuilder';
import { DcosCloneServerGroupModal } from './serverGroup/configure/DcosCloneServerGroupModal';
import { dcosServerGroupConfigurationService } from './serverGroup/configure/configuration.service';
import {
  DcosServerGroupActions,
  dcosServerGroupDetailsGetter,
  dcosServerGroupDetailsSections,
} from './serverGroup/details/dcosServerGroupDetails';
import { dcosServerGroupTransformer } from './serverGroup/transformer';
import './validation/applicationName.validator';

import './logo/dcos.logo.less';

function dcosLoadBalancerTransformerFactory() {
  return dcosLoadBalancerTransformer;
}

function dcosImageReaderFactory() {
  return dcosImageReader;
}

function dcosServerGroupTransformerFactory() {
  return dcosServerGroupTransformer;
}

function dcosServerGroupCommandBuilderFactory() {
  return dcosServerGroupCommandBuilder;
}

function dcosServerGroupConfigurationServiceFactory() {
  return dcosServerGroupConfigurationService;
}

export function registerDcosProvider() {
  CloudProviderRegistry.registerProvider('dcos', {
    name: 'DC/OS',
    logo: {
      path: logo,
    },
    instance: {
      details: DcosInstanceDetails,
    },
    loadBalancer: {
      transformer: dcosLoadBalancerTransformerFactory,
      useDetailsHook: useDcosLoadBalancerDetails,
      detailsActions: DcosLoadBalancerActions,
      detailsSections: dcosLoadBalancerDetailsSections,
      CreateLoadBalancerModal: DcosCreateLoadBalancerModal,
    },
    image: {
      reader: dcosImageReaderFactory,
    },
    serverGroup: {
      skipUpstreamStageCheck: true,
      transformer: dcosServerGroupTransformerFactory,
      commandBuilder: dcosServerGroupCommandBuilderFactory,
      configurationService: dcosServerGroupConfigurationServiceFactory,
      detailsGetter: dcosServerGroupDetailsGetter,
      detailsActions: DcosServerGroupActions,
      detailsSections: dcosServerGroupDetailsSections,
      CloneServerGroupModal: DcosCloneServerGroupModal,
    },
  });
}

registerDcosProvider();
