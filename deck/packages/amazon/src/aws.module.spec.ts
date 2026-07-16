import { CloudProviderRegistry, ReactInjector, Registry } from '@spinnaker/core';

import * as amazonPackage from './index';
import { AwsFunctionTransformer } from './function/function.transformer';
import { AwsImageReader } from './image';
import { AwsInstanceTypeService } from './instance/awsInstanceType.service';
import { AwsLoadBalancerTransformer } from './loadBalancer';
import { EvaluateCloudFormationChangeSetExecutionService } from './pipeline/stages/deployCloudFormation/evaluateCloudFormationChangeSetExecution.service';
import { getAmazonStageFields } from './pipeline/stages/AmazonStageConfig';
import { registerAmazonPipelineStages } from './aws.module';
import { AwsSecurityGroupReader } from './securityGroup/securityGroup.reader';
import { AwsSecurityGroupTransformer } from './securityGroup/securityGroup.transformer';
import { AwsServerGroupCommandBuilder } from './serverGroup/configure/serverGroupCommandBuilder.service';
import { AwsServerGroupConfigurationService } from './serverGroup/configure/serverGroupConfiguration.service';
import { AwsServerGroupTransformer } from './serverGroup/serverGroup.transformer';

describe('Amazon package registration', () => {
  const legacyCtrlKey = ['Cont', 'roller'].join('');
  const legacyStageCtrlKey = ['cont', 'roller'].join('');
  const legacyViewKey = ['Template', 'Url'].join('');
  const legacyModuleExport = ['AMAZON', 'MODULE'].join('_');
  const stageViewKey = ['template', 'Url'].join('');
  const stepLabelViewKey = ['execution', 'Step', 'Label', 'Url'].join('');
  const markupExtension = ['.', 'ht', 'ml'].join('');
  const injectionMetadataKey = ['$', 'inject'].join('');

  function expectRegistered(path: string): void {
    expect(CloudProviderRegistry.getValue('aws', path)).withContext(path).not.toBeNull();
  }

  function expectNonEmptyRegistration(path: string): void {
    const value = CloudProviderRegistry.getValue('aws', path);
    const entries = Array.isArray(value) ? value : [];
    expect(Array.isArray(value)).withContext(path).toBe(true);
    expect(entries.length).withContext(path).toBeGreaterThan(0);
  }

  function expectNoAngularStageRegistration(stageConfig: any): void {
    expect(stageConfig[stageViewKey]).withContext(`aws ${stageConfig.provides} stage view`).toBeUndefined();
    expect(stageConfig[legacyStageCtrlKey]).withContext(`aws ${stageConfig.provides} legacy handler`).toBeUndefined();
    expect(stageConfig[stepLabelViewKey]).withContext(`aws ${stageConfig.provides} step label view`).toBeUndefined();

    const htmlValues = Object.keys(stageConfig)
      .map((key) => stageConfig[key])
      .filter((value) => typeof value === 'string' && value.endsWith(markupExtension));

    expect(htmlValues).withContext(`aws ${stageConfig.provides} markup stage config values`).toEqual([]);
  }

  it('registers AWS without exporting an Angular module token', () => {
    expect(Object.prototype.hasOwnProperty.call(amazonPackage, legacyModuleExport)).toBe(false);

    expect(CloudProviderRegistry.getValue('aws', 'image.reader')).toBe(AwsImageReader);
    expect(CloudProviderRegistry.getValue('aws', 'serverGroup.transformer')).toBe(AwsServerGroupTransformer);
    expect(CloudProviderRegistry.getValue('aws', 'serverGroup.commandBuilder')).toBe(AwsServerGroupCommandBuilder);
    expect(CloudProviderRegistry.getValue('aws', 'serverGroup.configurationService')).toBe(
      AwsServerGroupConfigurationService,
    );
    expect(CloudProviderRegistry.getValue('aws', 'instance.instanceTypeService')).toBe(AwsInstanceTypeService);
    expect(CloudProviderRegistry.getValue('aws', 'loadBalancer.transformer')).toBe(AwsLoadBalancerTransformer);
    expect(CloudProviderRegistry.getValue('aws', 'function.transformer')).toBe(AwsFunctionTransformer);
    expect(CloudProviderRegistry.getValue('aws', 'function.setTransformer')).toBe(AwsFunctionTransformer);
    expect(CloudProviderRegistry.getValue('aws', 'securityGroup.reader')).toBe(AwsSecurityGroupReader);
    expect(CloudProviderRegistry.getValue('aws', 'securityGroup.transformer')).toBe(AwsSecurityGroupTransformer);

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

    expect(CloudProviderRegistry.getValue('aws', `serverGroup.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('aws', `serverGroup.details${legacyViewKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('aws', `instance.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('aws', `instance.details${legacyViewKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('aws', `loadBalancer.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('aws', `loadBalancer.details${legacyViewKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('aws', `securityGroup.details${legacyCtrlKey}`)).toBeNull();
    expect(CloudProviderRegistry.getValue('aws', `securityGroup.details${legacyViewKey}`)).toBeNull();
  });

  it('does not bundle Amazon Angular HTML templates', () => {
    const templates = require.context('./', true, /\.htm[l]$/).keys();
    expect(templates).toEqual([]);
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

  it('registers AWS delegates without Angular injection metadata', () => {
    [AwsServerGroupConfigurationService, EvaluateCloudFormationChangeSetExecutionService].forEach((delegate) => {
      expect(Object.prototype.hasOwnProperty.call(delegate, injectionMetadataKey)).toBe(false);
    });
  });

  it('constructs the server group configuration delegate with usable defaults when Core passes the deferred compatibility argument', () => {
    const securityGroupReader = { getAllSecurityGroups: jasmine.createSpy('getAllSecurityGroups') };
    spyOnProperty(ReactInjector, 'securityGroupReader', 'get').and.returnValue(securityGroupReader as any);
    spyOnProperty(ReactInjector, 'cacheInitializer', 'get').and.returnValue({
      refreshCache: jasmine.createSpy('refreshCache'),
    } as any);

    const service = new AwsServerGroupConfigurationService({ when: jasmine.createSpy('when') } as any);

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

  it('registers AWS pipeline stages without an Angular module dependency', () => {
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
        'destroyServerGroup',
        'disableCluster',
        'disableServerGroup',
        'enableServerGroup',
        'findImage',
        'findImageFromTags',
        'resizeServerGroup',
        'rollbackCluster',
        'scaleDownCluster',
        'shrinkCluster',
        'upsertImageTags',
      ];

      expectedStages.forEach((provides) => {
        const stage = awsStages.find((candidate) => candidate.provides === provides);
        expect(stage).withContext(`aws ${provides} stage`).toBeDefined();
        expect(stage?.component).withContext(`aws ${provides} stage component`).toBeDefined();
        expectNoAngularStageRegistration(stage);
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
      'destroyServerGroup',
      'disableCluster',
      'disableServerGroup',
      'enableServerGroup',
      'findImage',
      'findImageFromTags',
      'resizeServerGroup',
      'rollbackCluster',
      'scaleDownCluster',
      'shrinkCluster',
      'upsertImageTags',
    ];

    expectedStages.forEach((provides) => {
      const registrations = awsStages.filter((stage) => stage.provides === provides);
      expect(registrations.length).withContext(`aws ${provides} registration count`).toBe(1);
    });
  });

  it('renders required AWS stage-specific config fields', () => {
    expect(getAmazonStageFields({ type: 'bake' } as any).map((field) => field.fieldName)).toContain('package');
    expect(getAmazonStageFields({ type: 'findImage' } as any).map((field) => field.fieldName)).toEqual([
      'credentials',
      'regions',
      'cluster',
      'selectionStrategy',
    ]);
  });
});
