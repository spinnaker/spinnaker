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
import { registerOracleBakeStage } from './pipeline/stages/bake/OracleBakeStageConfig';
import { registerOracleDestroyAsgStage } from './pipeline/stages/destroyAsg/OracleDestroyAsgStageConfig';
import { registerOracleDisableAsgStage } from './pipeline/stages/disableAsg/OracleDisableAsgStageConfig';
import { registerOracleFindAmiStage } from './pipeline/stages/findAmi/OracleFindAmiStageConfig';
import { registerOracleFindImageFromTagsStage } from './pipeline/stages/findImageFromTags/OracleFindImageFromTagsStageConfig';
import { registerOracleResizeAsgStage } from './pipeline/stages/resizeAsg/OracleResizeAsgStageConfig';
import { registerOracleScaleDownClusterStage } from './pipeline/stages/scaleDownCluster/OracleScaleDownClusterStageConfig';
import { registerOracleShrinkClusterStage } from './pipeline/stages/shrinkCluster/OracleShrinkClusterStageConfig';
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
      // Oracle does not provide a create or clone modal.
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
