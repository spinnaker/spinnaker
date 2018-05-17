import { mock } from 'angular';
import { map } from 'lodash';
import * as React from 'react';

import { IStage, ITriggerTypeConfig, IStageTypeConfig } from 'core/domain';
import { IRegion } from 'core/account/AccountService';
import { Registry } from 'core/registry';
import { ITriggerTemplateComponentProps } from '../manualExecution/TriggerTemplate';

const mockProviderAccount = {
  accountId: 'abc',
  name: 'foobarbaz',
  requiredGroupMembership: [] as string[],
  type: 'foobar',
  accountType: 'foo',
  challengeDestructiveActions: false,
  cloudProvider: 'foo',
  environment: 'bar',
  primaryAccount: false,
  regions: [] as IRegion[],
  authorized: true,
};

const awsProviderAccount = {
  ...mockProviderAccount,
  cloudProvider: 'aws',
  providerVersion: 'foo',
};

const titusProviderAccount = {
  ...mockProviderAccount,
  cloudProvider: 'titus',
  providerVersion: 'foo',
};

const gcpProviderAccount = {
  ...mockProviderAccount,
  cloudProvider: 'gcp',
  providerVersion: 'foo',
};

describe('PipelineRegistry: API', function() {
  beforeEach(() => Registry.initialize());

  describe('registration', function() {
    it(
      'registers triggers',
      mock.inject(function() {
        expect(Registry.pipeline.getTriggerTypes().length).toBe(0);
        Registry.pipeline.registerTrigger({ key: 'cron' } as ITriggerTypeConfig);
        Registry.pipeline.registerTrigger({ key: 'pipeline' } as ITriggerTypeConfig);
        expect(Registry.pipeline.getTriggerTypes().length).toBe(2);
      }),
    );

    it(
      'registers stages',
      mock.inject(function() {
        expect(Registry.pipeline.getStageTypes().length).toBe(0);
        Registry.pipeline.registerStage({ key: 'a' } as IStageTypeConfig);
        Registry.pipeline.registerStage({ key: 'b' } as IStageTypeConfig);
        expect(Registry.pipeline.getStageTypes().length).toBe(2);
      }),
    );

    it(
      'provides only non-synthetic stages, non-provider-specific stages',
      mock.inject(function() {
        Registry.pipeline.registerStage({ key: 'a' } as IStageTypeConfig);
        Registry.pipeline.registerStage({ key: 'b', synthetic: true } as IStageTypeConfig);
        Registry.pipeline.registerStage({ key: 'c', useBaseProvider: true } as IStageTypeConfig);
        Registry.pipeline.registerStage({ key: 'd', provides: 'c' } as IStageTypeConfig);
        expect(Registry.pipeline.getStageTypes().length).toBe(4);
        expect(Registry.pipeline.getConfigurableStageTypes().length).toBe(2);
      }),
    );

    it(
      'returns providers for a stage key',
      mock.inject(function() {
        Registry.pipeline.registerStage({ key: 'a' } as IStageTypeConfig);
        Registry.pipeline.registerStage({ key: 'b', synthetic: true } as IStageTypeConfig);
        Registry.pipeline.registerStage({ key: 'c', useBaseProvider: true } as IStageTypeConfig);
        Registry.pipeline.registerStage({ key: 'd', provides: 'c' } as IStageTypeConfig);
        Registry.pipeline.registerStage({ key: 'e', provides: 'c' } as IStageTypeConfig);
        expect(Registry.pipeline.getProvidersFor('c').length).toBe(2);
      }),
    );

    it(
      'returns providers of base stage for child key',
      mock.inject(function() {
        Registry.pipeline.registerStage({ key: 'c', useBaseProvider: true } as IStageTypeConfig);
        Registry.pipeline.registerStage({ nameToCheckInTest: 'a', key: 'd', provides: 'c' } as IStageTypeConfig);
        Registry.pipeline.registerStage({ nameToCheckInTest: 'b', provides: 'c' } as IStageTypeConfig);
        const providers = Registry.pipeline.getProvidersFor('d');
        expect(providers.length).toBe(2);
        expect(map(providers, 'nameToCheckInTest').sort()).toEqual(['a', 'b']);
      }),
    );

    it(
      'augments provider stages with parent keys, labels, manualExecutionComponents, and descriptions',
      mock.inject(function() {
        const CompA = ({  }: ITriggerTemplateComponentProps) => React.createElement('a');
        const baseStage = {
            key: 'c',
            useBaseProvider: true,
            description: 'c description',
            label: 'the c',
            manualExecutionComponent: CompA,
          },
          augmentedA = {
            key: 'd',
            provides: 'c',
            description: 'c description',
            label: 'the c',
            manualExecutionComponent: CompA,
          } as any,
          augmentedB = {
            key: 'e',
            provides: 'c',
            description: 'c description',
            label: 'the c',
            manualExecutionComponent: CompA,
          },
          augmentedC = {
            key: 'c',
            provides: 'c',
            description: 'c description',
            label: 'the c',
            manualExecutionComponent: CompA,
          };
        Registry.pipeline.registerStage(baseStage as IStageTypeConfig);
        Registry.pipeline.registerStage({ key: 'd', provides: 'c' } as IStageTypeConfig);
        Registry.pipeline.registerStage({ key: 'e', provides: 'c' } as IStageTypeConfig);
        Registry.pipeline.registerStage({ provides: 'c' } as IStageTypeConfig);
        const stageTypes = Registry.pipeline.getStageTypes();
        expect(stageTypes as any[]).toEqual([baseStage, augmentedA, augmentedB, augmentedC]);
        expect(Registry.pipeline.getStageConfig({ type: 'd' } as any)).toEqual(augmentedA);
      }),
    );

    it(
      'allows provider stages to override of label, description, manualExecutionComponent',
      mock.inject(function() {
        const CompA = ({  }: ITriggerTemplateComponentProps) => React.createElement('a');
        const CompB = ({  }: ITriggerTemplateComponentProps) => React.createElement('b');
        Registry.pipeline.registerStage({
          key: 'a',
          useBaseProvider: true,
          description: 'a1',
          label: 'aa',
          manualExecutionComponent: CompA,
        } as IStageTypeConfig);
        Registry.pipeline.registerStage({
          key: 'b',
          provides: 'a',
          description: 'b1',
          label: 'bb',
          manualExecutionComponent: CompB,
        } as IStageTypeConfig);
        Registry.pipeline.registerStage({ key: 'c', provides: 'a' } as IStageTypeConfig);
        expect(Registry.pipeline.getStageTypes() as any[]).toEqual([
          { key: 'a', useBaseProvider: true, description: 'a1', label: 'aa', manualExecutionComponent: CompA },
          { key: 'b', provides: 'a', description: 'b1', label: 'bb', manualExecutionComponent: CompB },
          { key: 'c', provides: 'a', description: 'a1', label: 'aa', manualExecutionComponent: CompA },
        ]);
      }),
    );

    it(
      'returns stage config when an alias is supplied',
      mock.inject(function() {
        const config: IStageTypeConfig = { key: 'a', alias: 'a1' } as IStageTypeConfig;
        Registry.pipeline.registerStage(config);
        expect(Registry.pipeline.getStageConfig({ type: 'a' } as IStage)).toEqual(config);
        expect(Registry.pipeline.getStageConfig({ type: 'a1' } as IStage)).toEqual(config);
        expect(Registry.pipeline.getStageConfig({ type: 'b' } as IStage)).toBe(null);
      }),
    );
  });

  describe('stage type retrieval', function() {
    describe('no provider configured', function() {
      it(
        'adds all providers to stages that do not have any provider configuration',
        mock.inject(function() {
          Registry.pipeline.registerStage({ key: 'a' } as IStageTypeConfig);
          const providerAccounts = [awsProviderAccount, gcpProviderAccount];
          expect(Registry.pipeline.getConfigurableStageTypes(providerAccounts) as any[]).toEqual([
            { key: 'a', cloudProviders: ['aws', 'gcp'] },
          ]);
        }),
      );
    });

    describe('cloud providers configured on stage', function() {
      it(
        'preserves providers that match passed in providers if configured with cloudProviders',
        mock.inject(function() {
          Registry.pipeline.registerStage({ key: 'a', providesFor: ['aws'] } as IStageTypeConfig);
          const providerAccounts = [awsProviderAccount, gcpProviderAccount];
          expect(Registry.pipeline.getConfigurableStageTypes(providerAccounts) as any[]).toEqual([
            { key: 'a', providesFor: ['aws'], cloudProviders: ['aws'] },
          ]);
        }),
      );

      it(
        'filters providers to those passed in',
        mock.inject(function() {
          Registry.pipeline.registerStage({ key: 'a', providesFor: ['aws', 'gcp'] } as IStageTypeConfig);
          expect(Registry.pipeline.getConfigurableStageTypes([gcpProviderAccount]) as any[]).toEqual([
            { key: 'a', providesFor: ['aws', 'gcp'], cloudProviders: ['gcp'] },
          ]);
        }),
      );

      it(
        'filters out stages that do not support passed in providers',
        mock.inject(function() {
          Registry.pipeline.registerStage({ key: 'a', providesFor: ['aws', 'gcp'] } as IStageTypeConfig);
          expect(Registry.pipeline.getConfigurableStageTypes([titusProviderAccount])).toEqual([]);
        }),
      );

      it(
        'filters out stages that do not support passed in providers',
        mock.inject(function() {
          Registry.pipeline.registerStage({ key: 'a', providesFor: ['aws', 'gcp'] } as IStageTypeConfig);
          expect(Registry.pipeline.getConfigurableStageTypes([titusProviderAccount])).toEqual([]);
        }),
      );
    });

    describe('single cloud provider configured on stage', function() {
      it(
        'retains cloud providers when matching passed in providers',
        mock.inject(function() {
          Registry.pipeline.registerStage({ key: 'a', cloudProvider: 'aws' } as IStageTypeConfig);
          expect(Registry.pipeline.getConfigurableStageTypes([awsProviderAccount]) as any[]).toEqual([
            { key: 'a', cloudProvider: 'aws', cloudProviders: ['aws'] },
          ]);
        }),
      );

      it(
        'filters stages when provider does not match',
        mock.inject(function() {
          Registry.pipeline.registerStage({ key: 'a', cloudProvider: 'aws' } as IStageTypeConfig);
          expect(Registry.pipeline.getConfigurableStageTypes([gcpProviderAccount])).toEqual([]);
        }),
      );
    });

    describe('base stages', function() {
      it(
        'returns stage implementation providers that match based on cloud provider',
        mock.inject(function() {
          Registry.pipeline.registerStage({ key: 'a', useBaseProvider: true } as IStageTypeConfig);
          Registry.pipeline.registerStage({ key: 'b', provides: 'a', cloudProvider: 'aws' } as IStageTypeConfig);
          expect(Registry.pipeline.getConfigurableStageTypes([awsProviderAccount]) as any[]).toEqual([
            { key: 'a', useBaseProvider: true, cloudProviders: ['aws'] },
          ]);
        }),
      );

      it(
        'filters stage implementations with no matching cloud provider',
        mock.inject(function() {
          Registry.pipeline.registerStage({ key: 'a', useBaseProvider: true } as IStageTypeConfig);
          Registry.pipeline.registerStage({ key: 'b', provides: 'a', cloudProvider: 'aws' } as IStageTypeConfig);
          expect(Registry.pipeline.getConfigurableStageTypes([gcpProviderAccount])).toEqual([]);
        }),
      );

      it(
        'aggregates and filters cloud providers',
        mock.inject(function() {
          Registry.pipeline.registerStage({ key: 'a', useBaseProvider: true } as IStageTypeConfig);
          Registry.pipeline.registerStage({ key: 'b', provides: 'a', cloudProvider: 'aws' } as IStageTypeConfig);
          Registry.pipeline.registerStage({ key: 'c', provides: 'a', cloudProvider: 'gcp' } as IStageTypeConfig);
          Registry.pipeline.registerStage({ key: 'd', provides: 'a', cloudProvider: 'titus' } as IStageTypeConfig);
          const providerAccounts = [awsProviderAccount, titusProviderAccount];
          expect(Registry.pipeline.getConfigurableStageTypes(providerAccounts) as any[]).toEqual([
            { key: 'a', useBaseProvider: true, cloudProviders: ['aws', 'titus'] },
          ]);
        }),
      );

      it(
        'prefers providesFor to cloudProvider when configured on an implementing stage',
        mock.inject(function() {
          Registry.pipeline.registerStage({ key: 'a', useBaseProvider: true } as IStageTypeConfig);
          Registry.pipeline.registerStage({
            key: 'b',
            provides: 'a',
            cloudProvider: 'aws',
            providesFor: ['aws', 'gcp', 'titus'],
          } as IStageTypeConfig);
          const providerAccounts = [awsProviderAccount, titusProviderAccount];
          expect(Registry.pipeline.getConfigurableStageTypes(providerAccounts) as any[]).toEqual([
            { key: 'a', useBaseProvider: true, cloudProviders: ['aws', 'titus'] },
          ]);
        }),
      );
    });
  });

  describe('manualExecutionComponents', function() {
    it('hasManualExecutionComponentForTriggerType returns false if nothing configured', function() {
      Registry.pipeline.registerTrigger({ key: 'a' } as ITriggerTypeConfig);
      expect(Registry.pipeline.hasManualExecutionComponentForTriggerType('a')).toBe(false);
      expect(Registry.pipeline.hasManualExecutionComponentForTriggerType('b')).toBe(false);
    });

    it('hasManualExecutionComponentForTriggerType returns true if declared and available', function() {
      const CompA = ({  }: ITriggerTemplateComponentProps) => React.createElement('a');
      Registry.pipeline.registerTrigger({ key: 'cron', manualExecutionComponent: CompA } as ITriggerTypeConfig);
      expect(Registry.pipeline.hasManualExecutionComponentForTriggerType('cron')).toBe(true);
    });

    it('getManualExecutionComponentForTriggerType returns null if nothing configured', function() {
      Registry.pipeline.registerTrigger({ key: 'a' } as ITriggerTypeConfig);
      expect(Registry.pipeline.getManualExecutionComponentForTriggerType('a')).toBe(null);
      expect(Registry.pipeline.getManualExecutionComponentForTriggerType('b')).toBe(null);
    });

    it('hasManualExecutionComponentForTriggerType returns handler if declared and available', function() {
      const CompA = ({  }: ITriggerTemplateComponentProps) => React.createElement('a');
      Registry.pipeline.registerTrigger({ key: 'cron', manualExecutionComponent: CompA } as ITriggerTypeConfig);
      expect(Registry.pipeline.getManualExecutionComponentForTriggerType('cron')).toEqual(CompA);
    });
  });
});
