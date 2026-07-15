import { CloudProviderRegistry, Registry } from '@spinnaker/core';

import { OracleImageReader } from './image/image.reader';
import * as oraclePackage from './index';
import { OracleLoadBalancerTransformer } from './loadBalancer/loadBalancer.transformer';
import { registerOraclePipelineStages } from './oracle.provider';
import { OracleSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { OracleSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { OracleServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { OracleServerGroupConfigurationService } from './serverGroup/configure/serverGroupConfiguration.service';
import { OracleServerGroupTransformer } from './serverGroup/serverGroup.transformer';

describe('Oracle provider registration', () => {
  it('registers direct provider delegates without exporting an Angular module token', () => {
    expect((oraclePackage as any)['ORACLE' + '_MODULE']).toBeUndefined();

    expect(CloudProviderRegistry.getValue('oracle', 'image.reader')).toBe(OracleImageReader);
    expect(CloudProviderRegistry.getValue('oracle', 'loadBalancer.transformer')).toBe(OracleLoadBalancerTransformer);
    expect(CloudProviderRegistry.getValue('oracle', 'serverGroup.transformer')).toBe(OracleServerGroupTransformer);
    expect(CloudProviderRegistry.getValue('oracle', 'serverGroup.commandBuilder')).toBe(
      OracleServerGroupCommandBuilder,
    );
    expect(CloudProviderRegistry.getValue('oracle', 'serverGroup.configurationService')).toBe(
      OracleServerGroupConfigurationService,
    );
    expect(CloudProviderRegistry.getValue('oracle', 'securityGroup.reader')).toBe(OracleSecurityGroupReader);
    expect(CloudProviderRegistry.getValue('oracle', 'securityGroup.transformer')).toBe(OracleSecurityGroupTransformer);

    expect(CloudProviderRegistry.getValue('oracle', 'loadBalancer.details')).toBeNull();
    expect(CloudProviderRegistry.getValue('oracle', 'serverGroup.detailsActions')).toBeDefined();
    expect(CloudProviderRegistry.getValue('oracle', 'instance.details')).toBeDefined();
    expect(CloudProviderRegistry.getValue('oracle', 'securityGroup.details')).toBeDefined();
  });

  it('registers Oracle pipeline stages during package import', () => {
    Registry.reinitialize();
    registerOraclePipelineStages();

    const stages = Registry.pipeline.getStageTypes();

    expect(stages.some((stage) => stage.provides === 'findImage' && stage.cloudProvider === 'oracle')).toBe(true);
    expect(stages.some((stage) => stage.provides === 'resizeServerGroup' && stage.cloudProvider === 'oracle')).toBe(
      true,
    );
    expect(stages.some((stage) => stage.provides === 'bake' && stage.cloudProvider === 'oracle')).toBe(true);
  });
});
