import { load } from 'js-yaml';
import { cloneDeep } from 'lodash';

import {
  buildTemplateConfig,
  initializeTemplateConfiguration,
  mergeTemplatePlan,
} from './PipelineTemplateConfigurationAdapters';
import type { IPipelineTemplate, IPipelineTemplateConfig } from './PipelineTemplateReader';
import './templateVariableRegistrations';
import type { IPipeline, IPipelineTemplateConfigV2 } from '../../../domain';

describe('PipelineTemplateConfigurationAdapters', () => {
  const template = (variables: any[]): IPipelineTemplate =>
    ({
      id: 'template-id',
      metadata: { description: '', name: 'Template', owner: 'owner@example.com' },
      protect: false,
      schema: '1',
      source: 'spinnaker://template-id',
      stages: [],
      variables,
    } as IPipelineTemplate);

  const v1Config = (variables: Record<string, any> = {}): IPipelineTemplateConfig =>
    ({
      application: 'app',
      id: 'pipeline-id',
      name: 'Pipeline',
      type: 'templatedPipeline',
      config: {
        schema: '1',
        pipeline: {
          application: 'app',
          name: 'Pipeline',
          pipelineConfigId: 'pipeline-id',
          template: { source: 'spinnaker://template-id' },
          variables,
        },
        configuration: { inherit: ['parameters', 'expectedArtifacts', 'triggers'] },
      },
    } as IPipelineTemplateConfig);

  const v2Config = (variables: Record<string, any> = {}): IPipelineTemplateConfigV2 =>
    ({
      application: 'app',
      id: 'pipeline-id',
      name: 'Pipeline',
      schema: 'v2',
      template: {
        artifactAccount: 'front50ArtifactCredentials',
        reference: 'spinnaker://template-id',
        type: 'front50/pipelineTemplate',
      },
      type: 'templatedPipeline',
      variables,
    } as IPipelineTemplateConfigV2);

  it('initializes V1 variables in template order with grouped metadata, current values, defaults, and validation', () => {
    const state = initializeTemplateConfiguration(
      template([
        { name: 'first', type: 'string', group: 'Deploy', defaultValue: 'default-first' },
        { name: 'loose', type: 'list' },
        { name: 'second', type: 'string', group: 'Deploy', defaultValue: 'default-second' },
        { name: 'objectValue', type: 'object', group: 'Advanced', defaultValue: { default: true } },
        { name: 'newValue', type: 'string', defaultValue: 'new-default' },
      ]),
      v1Config({ first: 'configured', objectValue: { configured: true }, removed: 'prune me' }),
    );

    expect(state.isV2).toBe(false);
    expect(state.pipelineName).toBe('Pipeline');
    expect(state.source).toBe('spinnaker://template-id');
    expect(state.variableMetadataGroups.map((group) => group.name)).toEqual(['Deploy', 'Ungrouped', 'Advanced']);
    expect(state.variableMetadataGroups[0].variableMetadata.map((variable) => variable.name)).toEqual([
      'first',
      'second',
    ]);
    expect(state.variables.map((variable) => variable.name)).toEqual([
      'first',
      'loose',
      'second',
      'objectValue',
      'newValue',
    ]);
    expect(state.variables.find((variable) => variable.name === 'first').value).toBe('configured');
    expect(state.variables.find((variable) => variable.name === 'loose').value).toEqual(['']);
    expect(state.variables.find((variable) => variable.name === 'newValue').value).toBe('new-default');
    expect(load(state.variables.find((variable) => variable.name === 'objectValue').value)).toEqual({
      configured: true,
    });
    expect(state.variables.every((variable) => variable.hideErrors)).toBe(true);
    expect(state.variables.find((variable) => variable.name === 'second').errors).toEqual([]);
  });

  it('detects V2 configs and initializes JSON object and boolean values from their V2 locations', () => {
    const config = v2Config({ objectValue: { configured: true }, enabled: false });
    config.exclude = ['parameters', 'triggers'];

    const state = initializeTemplateConfiguration(
      template([
        { name: 'objectValue', type: 'object' },
        { name: 'enabled', type: 'boolean', defaultValue: true },
        { name: 'required', type: 'string' },
      ]),
      config,
    );

    expect(state.isV2).toBe(true);
    expect(state.source).toBe('spinnaker://template-id');
    expect(state.variables.find((variable) => variable.name === 'objectValue').value).toBe('{"configured":true}');
    expect(state.variables.find((variable) => variable.name === 'enabled').value).toBe(false);
    expect(state.variables.find((variable) => variable.name === 'required').errors).toEqual([
      { message: 'Field is required.' },
    ]);
    expect(state.inheritance).toEqual({
      inheritTemplateExpectedArtifacts: true,
      inheritTemplateNotifications: true,
      inheritTemplateParameters: false,
      inheritTemplateTriggers: false,
    });
  });

  it('builds an exact V1 templatedPipeline config and converts every variable type', () => {
    const original = v1Config();
    (original as any).customTopLevel = { preserve: true };
    const state = initializeTemplateConfiguration(
      template([
        { name: 'integer', type: 'int', defaultValue: 3 },
        { name: 'decimal', type: 'float', defaultValue: 2.5 },
        { name: 'enabled', type: 'boolean', defaultValue: false },
        { name: 'items', type: 'list', defaultValue: ['one'] },
        { name: 'objectValue', type: 'object', defaultValue: { nested: true } },
        { name: 'text', type: 'string', defaultValue: 'hello' },
      ]),
      original,
    );
    state.variables.find((variable) => variable.name === 'integer').value = '42';
    state.variables.find((variable) => variable.name === 'decimal').value = '4.25';
    state.variables.find((variable) => variable.name === 'enabled').value = true;
    state.variables.find((variable) => variable.name === 'objectValue').value = 'nested: changed\n';
    state.inheritance.inheritTemplateTriggers = false;

    const result = buildTemplateConfig('renamed-app', 'pipeline-id', original, state) as IPipelineTemplateConfig;

    expect((result as any).customTopLevel).toEqual({ preserve: true });
    expect(result.config).toEqual({
      schema: '1',
      pipeline: {
        name: 'Pipeline',
        application: 'renamed-app',
        pipelineConfigId: 'pipeline-id',
        template: { source: 'spinnaker://template-id' },
        variables: {
          integer: 42,
          decimal: 4.25,
          enabled: true,
          items: ['one'],
          objectValue: { nested: 'changed' },
          text: 'hello',
        },
      },
      configuration: { inherit: ['parameters', 'expectedArtifacts'] },
    });
  });

  it('builds V2 exclude/reference config without mutating inherited collections on the input', () => {
    const original = v2Config({ stale: 'removed' });
    original.parameterConfig = [{ name: 'inherited', inherited: true }, { name: 'local' }] as any;
    original.notifications = [{ type: 'email', inherited: true }, { type: 'slack' }] as any;
    const snapshot = cloneDeep(original);
    const state = initializeTemplateConfiguration(
      template([
        { name: 'objectValue', type: 'object', defaultValue: { nested: true } },
        { name: 'integer', type: 'int', defaultValue: 1 },
      ]),
      original,
    );
    state.inheritance.inheritTemplateParameters = false;
    state.inheritance.inheritTemplateNotifications = false;
    state.inheritance.inheritTemplateTriggers = false;

    const result = buildTemplateConfig('app', 'pipeline-id', original, state) as IPipelineTemplateConfigV2;

    expect(original).toEqual(snapshot);
    expect(result.parameterConfig).toEqual([{ name: 'local' }] as any);
    expect(result.notifications).toEqual([{ type: 'slack' }] as any);
    expect(result.variables).toEqual({ objectValue: { nested: true }, integer: 1 });
    expect(result.exclude).toEqual(['parameters', 'notifications', 'triggers']);
    expect(result.schema).toBe('v2');
    expect(result.template).toEqual({
      artifactAccount: 'front50ArtifactCredentials',
      reference: 'spinnaker://template-id',
      type: 'front50/pipelineTemplate',
    });
  });

  it('merges only selected V1 plan collections', () => {
    const config = v1Config();
    const state = initializeTemplateConfiguration(template([]), config);
    state.inheritance.inheritTemplateExpectedArtifacts = false;
    state.inheritance.inheritTemplateTriggers = false;
    const plan = {
      parameterConfig: [{ name: 'parameter' }],
      expectedArtifacts: [{ id: 'artifact' }],
      triggers: [{ type: 'manual' }],
    } as IPipeline;

    expect(mergeTemplatePlan(config, plan, state)).toEqual(
      jasmine.objectContaining({ parameterConfig: plan.parameterConfig }),
    );
    expect(mergeTemplatePlan(config, plan, state).expectedArtifacts).toBeUndefined();
    expect(mergeTemplatePlan(config, plan, state).triggers).toBeUndefined();
  });

  it('removes stale V1 inherited collections before merging only the current plan selections', () => {
    const config = v1Config() as IPipelineTemplateConfig & IPipeline;
    config.parameterConfig = [{ name: 'stale-parameter' }];
    (config as any).parameters = [{ name: 'stale-legacy-parameter' }];
    config.expectedArtifacts = [{ id: 'stale-artifact' }] as any;
    config.triggers = [{ type: 'stale-trigger' }];
    const state = initializeTemplateConfiguration(template([]), config);
    state.inheritance.inheritTemplateExpectedArtifacts = false;
    state.inheritance.inheritTemplateTriggers = false;
    const plan = {
      parameterConfig: [{ name: 'current-parameter' }],
      expectedArtifacts: [{ id: 'current-artifact' }],
      triggers: [{ type: 'current-trigger' }],
    } as IPipeline;

    const built = buildTemplateConfig('app', 'pipeline-id', config, state) as IPipeline;
    const merged = mergeTemplatePlan(built, plan, state);

    expect(built.parameterConfig).toBeUndefined();
    expect((built as any).parameters).toBeUndefined();
    expect(built.expectedArtifacts).toBeUndefined();
    expect(built.triggers).toBeUndefined();
    expect(merged.parameterConfig).toEqual(plan.parameterConfig);
    expect((merged as any).parameters).toBeUndefined();
    expect(merged.expectedArtifacts).toBeUndefined();
    expect(merged.triggers).toBeUndefined();
  });

  it('always merges V2 expected artifacts and conditionally merges the other plan collections', () => {
    const config = v2Config();
    const state = initializeTemplateConfiguration(template([]), config);
    state.inheritance.inheritTemplateNotifications = false;
    state.inheritance.inheritTemplateTriggers = false;
    const plan = {
      parameterConfig: [{ name: 'parameter' }],
      notifications: [{ type: 'email' }],
      expectedArtifacts: [{ id: 'artifact' }],
      triggers: [{ type: 'manual' }],
    } as IPipeline;

    const result = mergeTemplatePlan(config, plan, state);

    expect(result.parameterConfig).toEqual(plan.parameterConfig);
    expect(result.expectedArtifacts).toEqual(plan.expectedArtifacts);
    expect(result.notifications).toBeUndefined();
    expect(result.triggers).toBeUndefined();
  });
});
