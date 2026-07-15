import type React from 'react';

import { CloudProviderRegistry, DeploymentStrategyRegistry } from '@spinnaker/core';

import './helpContents/oracleHelpContents';
import { OracleImageReader } from './image/image.reader';
import { OracleInstanceDetails } from './instance/details/OracleInstanceDetails';
import {
  OracleLoadBalancerDetailsSections,
  useOracleLoadBalancerDetails,
} from './loadBalancer/details/OracleLoadBalancerDetails';
import { OracleLoadBalancerTransformer } from './loadBalancer/loadBalancer.transformer';
import { registerOracleBakeStage } from './pipeline/stages/bake/ociBakeStage';
import { registerOracleDestroyAsgStage } from './pipeline/stages/destroyAsg/destroyAsgStage';
import { registerOracleDisableAsgStage } from './pipeline/stages/disableAsg/disableAsgStage';
import { registerOracleFindAmiStage } from './pipeline/stages/findAmi/findAmiStage';
import { registerOracleFindImageFromTagsStage } from './pipeline/stages/findImageFromTags/oracleFindImageFromTagsStage';
import { registerOracleResizeAsgStage } from './pipeline/stages/resizeAsg/resizeAsgStage';
import { registerOracleScaleDownClusterStage } from './pipeline/stages/scaleDownCluster/scaleDownClusterStage';
import { registerOracleShrinkClusterStage } from './pipeline/stages/shrinkCluster/shrinkClusterStage';
import { OracleSecurityGroupDetails } from './securityGroup/details/OracleSecurityGroupDetails';
import { OracleSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { OracleSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { OracleServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { OracleServerGroupConfigurationService } from './serverGroup/configure/serverGroupConfiguration.service';
import {
  OracleServerGroupActions,
  oracleServerGroupDetailsGetter,
  OracleServerGroupInformationSection,
  OracleServerGroupLaunchConfigSection,
  OracleServerGroupSizeSection,
} from './serverGroup/details/OracleServerGroupDetails';
import { OracleServerGroupTransformer } from './serverGroup/serverGroup.transformer';

export function registerOracleProvider(): void {
  CloudProviderRegistry.registerProvider('oracle', {
    name: 'Oracle',
    image: {
      reader: OracleImageReader,
    },
    loadBalancer: {
      transformer: OracleLoadBalancerTransformer,
      useDetailsHook: useOracleLoadBalancerDetails,
      detailsActions: function OracleLoadBalancerActions(): React.ReactElement | null {
        return null;
      },
      detailsSections: OracleLoadBalancerDetailsSections,
    },
    serverGroup: {
      transformer: OracleServerGroupTransformer,
      detailsActions: OracleServerGroupActions,
      detailsGetter: oracleServerGroupDetailsGetter,
      detailsSections: [
        OracleServerGroupInformationSection,
        OracleServerGroupSizeSection,
        OracleServerGroupLaunchConfigSection,
      ],
      // The Oracle create/clone wizard was Angular-only; register no modal until a React replacement exists.
      commandBuilder: OracleServerGroupCommandBuilder,
      configurationService: OracleServerGroupConfigurationService,
    },
    instance: {
      details: OracleInstanceDetails,
    },
    securityGroup: {
      reader: OracleSecurityGroupReader,
      transformer: OracleSecurityGroupTransformer,
      details: OracleSecurityGroupDetails,
    },
  });
}

export function registerOraclePipelineStages(): void {
  registerOracleBakeStage();
  registerOracleDestroyAsgStage();
  registerOracleDisableAsgStage();
  registerOracleFindAmiStage();
  registerOracleFindImageFromTagsStage();
  registerOracleResizeAsgStage();
  registerOracleScaleDownClusterStage();
  registerOracleShrinkClusterStage();
}

registerOracleProvider();
DeploymentStrategyRegistry.registerProvider('oracle', []);
