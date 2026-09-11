import type { IStageTypeConfig } from '@spinnaker/core';
import { CloudProviderRegistry, Registry } from '@spinnaker/core';

import './index';
import { AwsFunctionTransformer } from './function/function.transformer';
import { AwsImageReader } from './image';
import { AwsInstanceTypeService } from './instance/awsInstanceType.service';
import { AwsLoadBalancerTransformer } from './loadBalancer';
import { AmazonStageConfig, getAmazonStageFields } from './pipeline/stages/AmazonStageConfig';
import { awsBakeStage, AwsBakeStageConfig } from './pipeline/stages/bake/AwsBakeStageConfig';
import { AwsCloneServerGroupStageConfig } from './pipeline/stages/cloneServerGroup/AwsCloneServerGroupStageConfig';
import { DeployCloudFormationStackStageConfig } from './pipeline/stages/deployCloudFormation/DeployCloudFormationStackStageConfig';
import { AwsDisableAsgStageConfig } from './pipeline/stages/disableAsg/AwsDisableAsgStageConfig';
import { AwsDisableClusterStageConfig } from './pipeline/stages/disableCluster/AwsDisableClusterStageConfig';
import { AwsEnableAsgStageConfig } from './pipeline/stages/enableAsg/AwsEnableAsgStageConfig';
import { AwsFindImageFromTagsStageConfig } from './pipeline/stages/findImageFromTags/AwsFindImageFromTagsStageConfig';
import { ModifyScalingProcessStageConfig } from './pipeline/stages/modifyScalingProcess/ModifyScalingProcessStageConfig';
import { AwsResizeAsgStageConfig } from './pipeline/stages/resizeAsg/AwsResizeAsgStageConfig';
import { AwsRollbackClusterStageConfig } from './pipeline/stages/rollbackCluster/AwsRollbackClusterStageConfig';
import { AwsScaleDownClusterStageConfig } from './pipeline/stages/scaleDownCluster/AwsScaleDownClusterStageConfig';
import { AwsShrinkClusterStageConfig } from './pipeline/stages/shrinkCluster/AwsShrinkClusterStageConfig';
import { AwsTagImageStageConfig } from './pipeline/stages/tagImage/awsTagImageStage';
import { registerAmazonPipelineStages } from './aws.module';
import { AwsSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { AwsSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { AwsServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import {
  AwsServerGroupConfigurationService,
  AwsServerGroupConfigurationServiceDelegate,
} from './serverGroup/configure/serverGroupConfiguration.service';
import { AwsServerGroupTransformer } from './serverGroup/serverGroup.transformer';

describe('Amazon package registration', () => {
  function expectRegistered(path: string): void {
    expect(CloudProviderRegistry.getValue('aws', path)).withContext(path).not.toBeNull();
  }

  function expectNonEmptyRegistration(path: string): void {
    const value = CloudProviderRegistry.getValue('aws', path);
    const entries = Array.isArray(value) ? value : [];
    expect(Array.isArray(value)).withContext(path).toBe(true);
    expect(entries.length).withContext(path).toBeGreaterThan(0);
  }

  it('does not register function details as a provider override', () => {
    const overrideValue = spyOn(CloudProviderRegistry, 'overrideValue');
    const functionDetailsModule = require.resolve('./function/details/AmazonFunctionDetails');
    delete require.cache[functionDetailsModule];

    require('./function/details/AmazonFunctionDetails');

    expect(overrideValue).not.toHaveBeenCalled();
  });

  it('registers AWS provider values', () => {
    expect(CloudProviderRegistry.getValue('aws', 'image.reader')).toBe(AwsImageReader);
    expect(CloudProviderRegistry.getValue('aws', 'serverGroup.transformer')).toBe(AwsServerGroupTransformer);
    expect(CloudProviderRegistry.getValue('aws', 'serverGroup.commandBuilder')).toBe(AwsServerGroupCommandBuilder);
    expect(CloudProviderRegistry.getValue('aws', 'serverGroup.configurationService')).toBe(
      AwsServerGroupConfigurationServiceDelegate,
    );
    expect(CloudProviderRegistry.getValue('aws', 'instance.instanceTypeService')).toBe(AwsInstanceTypeService);
    expect(CloudProviderRegistry.getValue('aws', 'loadBalancer.transformer')).toBe(AwsLoadBalancerTransformer);
    expect(CloudProviderRegistry.getValue('aws', 'function.transformer')).toBe(AwsFunctionTransformer);
    expect(CloudProviderRegistry.getValue('aws', 'function.setTransformer')).toBe(AwsFunctionTransformer);
    expect(CloudProviderRegistry.getValue('aws', 'securityGroup.reader')).toBe(AwsSecurityGroupReader);
    expect(CloudProviderRegistry.getValue('aws', 'securityGroup.transformer')).toBe(AwsSecurityGroupTransformer);
    expect(CloudProviderRegistry.getValue('aws', 'applicationProviderFields')).toEqual([
      {
        field: 'useAmiBlockDeviceMappings',
        label: 'Prefer AMI Block Device Mappings',
        type: 'boolean',
      },
    ]);

    expectRegistered('serverGroup.CloneServerGroupModal');
    expectRegistered('serverGroup.detailsGetter');
    expectRegistered('serverGroup.detailsActions');
    expectNonEmptyRegistration('serverGroup.detailsSections');
    expectRegistered('instance.details');
    expectRegistered('loadBalancer.CreateLoadBalancerModal');
    expectRegistered('loadBalancer.useDetailsHook');
    expectRegistered('loadBalancer.detailsActions');
    expectNonEmptyRegistration('loadBalancer.detailsSections');
    expectRegistered('loadBalancer.targetGroupDetails');
    expectRegistered('function.details');
    expectRegistered('function.CreateFunctionModal');
    expectRegistered('securityGroup.CreateSecurityGroupModal');
    expectRegistered('securityGroup.details');
  });

  it('registers AWS provider delegates as direct constructors', () => {
    expect(typeof CloudProviderRegistry.getValue('aws', 'serverGroup.transformer')).toBe('function');
    expect(typeof CloudProviderRegistry.getValue('aws', 'serverGroup.commandBuilder')).toBe('function');
    expect(typeof CloudProviderRegistry.getValue('aws', 'serverGroup.configurationService')).toBe('function');
    expect(typeof CloudProviderRegistry.getValue('aws', 'instance.instanceTypeService')).toBe('function');
    expect(typeof CloudProviderRegistry.getValue('aws', 'loadBalancer.transformer')).toBe('function');
    expect(typeof CloudProviderRegistry.getValue('aws', 'function.transformer')).toBe('function');
    expect(typeof CloudProviderRegistry.getValue('aws', 'function.setTransformer')).toBe('function');
    expect(typeof CloudProviderRegistry.getValue('aws', 'securityGroup.reader')).toBe('function');
    expect(typeof CloudProviderRegistry.getValue('aws', 'securityGroup.transformer')).toBe('function');
  });

  it('constructs the server group configuration service with explicit dependencies', () => {
    const securityGroupReader = { getAllSecurityGroups: jasmine.createSpy('getAllSecurityGroups') };
    const cacheInitializer = {
      refreshCache: jasmine.createSpy('refreshCache'),
    };

    const service = new AwsServerGroupConfigurationService(
      securityGroupReader as any,
      new AwsInstanceTypeService(),
      cacheInitializer as any,
    );

    expect((service as any).securityGroupReader).toBe(securityGroupReader);
    expect(() => service.applyOverrides('beforeConfiguration', {} as any)).not.toThrow();

    const command = {
      region: 'us-east-1',
      virtualizationType: 'hvm',
      vpcId: 'vpc-1',
      amiArchitecture: 'x86_64',
      backingData: {
        instanceTypesInfo: {
          'us-east-1': [
            {
              name: 'm5.large',
              supportedVirtualizationTypes: ['hvm'],
              supportedArchitectures: ['x86_64'],
            },
          ],
        },
        filtered: {},
      },
      launchTemplateOverridesForInstanceType: [],
      viewState: { dirty: {} },
    } as any;

    expect(() => service.configureInstanceTypes(command)).not.toThrow();
    expect(command.backingData.filtered.instanceTypes).toEqual(['m5.large']);
  });

  it('registers AWS pipeline stages with React components', () => {
    const previousPipeline = Registry.pipeline;
    const previousUrlBuilder = Registry.urlBuilder;

    Registry.reinitialize();
    try {
      registerAmazonPipelineStages();

      const stages = Registry.pipeline.getStageTypes();

      const awsStages = stages.filter((stage) => stage.cloudProvider === 'aws');
      const expectedStages = [
        'bake',
        'cloneServerGroup',
        'deployCloudFormation',
        'destroyServerGroup',
        'disableCluster',
        'disableServerGroup',
        'enableServerGroup',
        'findImage',
        'findImageFromTags',
        'modifyAwsScalingProcess',
        'resizeServerGroup',
        'rollbackCluster',
        'scaleDownCluster',
        'shrinkCluster',
        'upsertImageTags',
      ];

      expectedStages.forEach((provides) => {
        const stage = awsStages.find((candidate) => (candidate.provides || candidate.key) === provides);
        expect(stage).withContext(`aws ${provides} stage`).toBeDefined();
        expect(stage?.component).withContext(`aws ${provides} stage component`).toBeDefined();
      });

      const structuredStages: Array<{
        component: IStageTypeConfig['component'];
        key: string;
      }> = [
        {
          key: 'bake',
          component: AwsBakeStageConfig,
        },
        {
          key: 'cloneServerGroup',
          component: AwsCloneServerGroupStageConfig,
        },
        {
          key: 'deployCloudFormation',
          component: DeployCloudFormationStackStageConfig,
        },
        {
          key: 'disableServerGroup',
          component: AwsDisableAsgStageConfig,
        },
        {
          key: 'disableCluster',
          component: AwsDisableClusterStageConfig,
        },
        {
          key: 'enableServerGroup',
          component: AwsEnableAsgStageConfig,
        },
        {
          key: 'upsertImageTags',
          component: AwsTagImageStageConfig,
        },
        {
          key: 'findImageFromTags',
          component: AwsFindImageFromTagsStageConfig,
        },
        {
          key: 'resizeServerGroup',
          component: AwsResizeAsgStageConfig,
        },
        {
          key: 'rollbackCluster',
          component: AwsRollbackClusterStageConfig,
        },
        {
          key: 'scaleDownCluster',
          component: AwsScaleDownClusterStageConfig,
        },
        {
          key: 'shrinkCluster',
          component: AwsShrinkClusterStageConfig,
        },
        {
          key: 'modifyAwsScalingProcess',
          component: ModifyScalingProcessStageConfig,
        },
      ];

      structuredStages.forEach(({ component, key }) => {
        const stage = awsStages.find((candidate) => (candidate.key || candidate.provides) === key);
        expect(stage).withContext(`aws ${key} structured stage`).toBeDefined();
        expect(stage?.key).withContext(`aws ${key} stage key`).toBe(key);
        expect(stage?.cloudProvider).withContext(`aws ${key} cloud provider`).toBe('aws');
        expect(stage?.component).withContext(`aws ${key} config component`).toBe(component);
      });

      const deployCloudFormation = awsStages.find((stage) => stage.key === 'deployCloudFormation');
      expect(deployCloudFormation?.executionDetailsSections?.length)
        .withContext('aws deployCloudFormation React execution details')
        .toBeGreaterThan(0);

      ['destroyServerGroup', 'findImage'].forEach((key) => {
        const stage = awsStages.find((candidate) => (candidate.key || candidate.provides) === key);
        expect(stage?.component).withContext(`aws ${key} generic stage component`).toBe(AmazonStageConfig);
      });
    } finally {
      Registry.pipeline = previousPipeline;
      Registry.urlBuilder = previousUrlBuilder;
    }
  });

  it('does not duplicate AWS pipeline stages on module import', () => {
    const awsStages = Registry.pipeline.getStageTypes().filter((stage) => stage.cloudProvider === 'aws');
    const expectedStages = [
      'bake',
      'cloneServerGroup',
      'deployCloudFormation',
      'destroyServerGroup',
      'disableCluster',
      'disableServerGroup',
      'enableServerGroup',
      'findImage',
      'findImageFromTags',
      'modifyAwsScalingProcess',
      'resizeServerGroup',
      'rollbackCluster',
      'scaleDownCluster',
      'shrinkCluster',
      'upsertImageTags',
    ];

    expectedStages.forEach((provides) => {
      const registrations = awsStages.filter((stage) => (stage.provides || stage.key) === provides);
      expect(registrations.length).withContext(`aws ${provides} registration count`).toBe(1);
    });
  });

  it('renders required AWS stage-specific config fields', () => {
    expect(awsBakeStage.validators.map((validator: any) => validator.fieldName)).toContain('package');
    expect(getAmazonStageFields({ type: 'findImage' } as any).map((field) => field.fieldName)).toEqual([
      'credentials',
      'regions',
      'cluster',
      'selectionStrategy',
      'onlyEnabled',
    ]);
  });
});
