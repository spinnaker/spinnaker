import { mount, shallow } from 'enzyme';
import React from 'react';

import {
  AccountSelectInput,
  AccountService,
  CheckboxInput,
  ChecklistInput,
  MapEditor,
  ReactSelectInput,
  Registry,
  StageArtifactSelectorDelegate,
  StageConfigField,
  TextInput,
  YamlEditor,
} from '@spinnaker/core';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { CloudFormationChangeSetInfo } from './CloudFormationChangeSetInfo';
import { DeployCloudFormationStackStageConfig } from './DeployCloudFormationStackStageConfig';
import { registerDeployCloudFormationStackStage } from './deployCloudFormationStackStage';

describe('Deploy CloudFormation stack stage', () => {
  beforeEach(() => {
    spyOn(AccountService, 'getAllAccountDetailsForProvider').and.returnValue(Promise.resolve([]));
    spyOn(AccountService, 'getArtifactAccounts').and.returnValue(Promise.resolve([]));
    spyOn(AccountService, 'getUniqueAttributeForAllAccounts').and.returnValue(Promise.resolve([]));
  });

  function renderEditor(stage: any = {}, application: any = {}) {
    const updateStageField = jasmine.createSpy('updateStageField');
    const wrapper = shallow(
      <DeployCloudFormationStackStageConfig
        application={application}
        pipeline={{ expectedArtifacts: [] } as any}
        stage={{ type: 'deployCloudFormation', ...stage }}
        updateStageField={updateStageField}
      />,
    );
    return { updateStageField, wrapper };
  }

  function mountEditor(stage: any = {}, application: any = {}) {
    const updateStageField = jasmine.createSpy('updateStageField');
    const wrapper = mount(
      <DeployCloudFormationStackStageConfig
        application={application}
        pipeline={{ expectedArtifacts: [] } as any}
        stage={{ type: 'deployCloudFormation', ...stage }}
        updateStageField={updateStageField}
      />,
    );
    return { updateStageField, wrapper };
  }

  it('registers a dedicated stage editor', () => {
    const registerStage = spyOn(Registry.pipeline, 'registerStage');

    registerDeployCloudFormationStackStage();

    expect(registerStage.calls.mostRecent().args[0].component).not.toBe(AmazonStageConfig);
  });

  it('renders all text-template settings without changing the stage on mount', () => {
    const parameters = { Environment: 'production' };
    const tags = { Team: 'payments' };
    const templateBody = { Resources: { Queue: { Type: 'AWS::SQS::Queue' } } };
    const capabilities = ['CAPABILITY_IAM', 'CAPABILITY_AUTO_EXPAND'];
    const { updateStageField, wrapper } = renderEditor({
      account: 'test-account',
      cloudProvider: 'aws',
      credentials: 'test-account',
      regions: ['eu-west-1'],
      stackName: 'payment-stack',
      roleARN: 'arn:aws:iam::123456789012:role/cloudformation',
      source: 'text',
      templateBody,
      parameters,
      tags,
      capabilities,
    });

    expect(wrapper.find(AccountSelectInput).exists()).toBe(true);
    if (!wrapper.find(AccountSelectInput).exists()) {
      return;
    }
    expect(wrapper.find(AccountSelectInput).prop('value')).toBe('test-account');
    expect(wrapper.find(ChecklistInput).prop('value')).toEqual(['eu-west-1']);
    expect(wrapper.find('input[name="stackName"]').prop('value')).toBe('payment-stack');
    expect(wrapper.find('input[name="roleARN"]').prop('value')).toBe('arn:aws:iam::123456789012:role/cloudformation');
    expect(wrapper.find('input[name="source"][value="text"]').prop('checked')).toBe(true);
    expect(wrapper.find(YamlEditor).prop('value')).toContain('AWS::SQS::Queue');
    expect(wrapper.find(MapEditor).at(0).prop('model')).toBe(parameters);
    expect(wrapper.find(MapEditor).at(1).prop('model')).toBe(tags);
    expect(wrapper.find(ReactSelectInput).prop('value')).toBe(capabilities);
    expect(updateStageField).not.toHaveBeenCalled();
  });

  it('initializes only missing required fields for a new stage', () => {
    const { updateStageField } = mountEditor(
      {},
      {
        defaultCredentials: { aws: 'default-account' },
        defaultRegions: { aws: 'eu-west-1' },
      },
    );

    expect(updateStageField.calls.allArgs()).toEqual([
      [
        {
          account: 'default-account',
          capabilities: [],
          cloudProvider: 'aws',
          credentials: 'default-account',
          parameters: {},
          regions: ['eu-west-1'],
          source: 'text',
          tags: {},
        },
      ],
    ]);
  });

  it('preserves explicit persisted-stage values without repeating initialization', () => {
    const stage = {
      account: 'persisted-account',
      capabilities: [],
      cloudProvider: 'persisted-provider',
      credentials: 'persisted-credentials',
      parameters: {},
      regions: [],
      source: 'artifact',
      tags: {},
    };
    const application = {
      defaultCredentials: { aws: 'default-account' },
      defaultRegions: { aws: 'eu-west-1' },
    };
    const { updateStageField, wrapper } = mountEditor(stage, application);

    wrapper.setProps({ application: { ...application }, stage: { ...stage } });

    expect(updateStageField).not.toHaveBeenCalled();
    expect(wrapper.find('input[name="source"][value="artifact"]').prop('checked')).toBe(true);
  });

  it('preserves a string template body in the YAML editor', () => {
    const templateBody = 'Resources:\n  Queue:\n    Type: AWS::SQS::Queue';

    const { wrapper } = renderEditor({ source: 'text', templateBody });

    expect(wrapper.find(YamlEditor).exists()).toBe(true);
    if (!wrapper.find(YamlEditor).exists()) {
      return;
    }
    expect(wrapper.find(YamlEditor).prop('value')).toBe(templateBody);
  });

  it('updates stack identity and template source fields', () => {
    const { updateStageField, wrapper } = renderEditor();

    expect(wrapper.find(AccountSelectInput).exists()).toBe(true);
    if (!wrapper.find(AccountSelectInput).exists()) {
      return;
    }
    wrapper.find(AccountSelectInput).prop('onChange')({ target: { value: 'other-account' } } as any);
    wrapper.find(ChecklistInput).prop('onChange')({ target: { value: ['us-east-1'] } } as any);
    wrapper.find('input[name="stackName"]').simulate('change', { target: { value: 'other-stack' } });
    wrapper.find('input[name="roleARN"]').simulate('change', { target: { value: 'other-role' } });
    wrapper.find('input[name="isChangeSet"]').simulate('change', { target: { checked: true } });
    wrapper.find('input[name="source"][value="artifact"]').simulate('change');

    expect(updateStageField.calls.allArgs()).toEqual([
      [{ credentials: 'other-account', account: 'other-account' }],
      [{ regions: ['us-east-1'] }],
      [{ stackName: 'other-stack' }],
      [{ roleARN: 'other-role' }],
      [{ isChangeSet: true }],
      [{ source: 'artifact' }],
    ]);
  });

  it('retains valid raw YAML formatting and comments after a parent rerender', () => {
    const stage = {
      capabilities: [],
      cloudProvider: 'aws',
      parameters: {},
      regions: [],
      source: 'text',
      tags: {},
      templateBody: [{ Resources: {} }],
    };
    const parsedTemplate = [{ Resources: { Queue: { Type: 'AWS::SQS::Queue' } } }];
    const rawTemplateBody = '# queue template\nResources:\n  Queue: { Type: AWS::SQS::Queue }\n';
    const { updateStageField, wrapper } = mountEditor(stage);

    wrapper.find(YamlEditor).prop('onChange')(rawTemplateBody, parsedTemplate);
    wrapper.setProps({
      stage: { type: 'deployCloudFormation', ...stage, templateBody: parsedTemplate },
    });

    expect(updateStageField.calls.allArgs()).toEqual([[{ templateBody: parsedTemplate }]]);
    expect(wrapper.find(YamlEditor).prop('value')).toBe(rawTemplateBody);
    wrapper.unmount();
  });

  it('retains invalid raw YAML after a parent rerender without changing templateBody', () => {
    const stage = {
      capabilities: [],
      cloudProvider: 'aws',
      parameters: {},
      regions: [],
      source: 'text',
      tags: {},
      templateBody: [{ Resources: {} }],
    };
    const rawTemplateBody = '# incomplete edit\nResources: [';
    const { updateStageField, wrapper } = mountEditor(stage);

    wrapper.find(YamlEditor).prop('onChange')(rawTemplateBody, null);
    wrapper.setProps({
      stage: { type: 'deployCloudFormation', ...stage },
    });

    expect(updateStageField).not.toHaveBeenCalled();
    expect(wrapper.find(YamlEditor).prop('value')).toBe(rawTemplateBody);
    wrapper.unmount();
  });

  it('resets raw YAML when the stage refId changes without updating the new stage', () => {
    const firstStage = {
      capabilities: [],
      cloudProvider: 'aws',
      parameters: {},
      refId: '1',
      regions: [],
      source: 'text',
      tags: {},
      templateBody: [{ Resources: {} }],
    };
    const firstRawTemplateBody = '# first stage\nResources:\n  First: {}\n';
    const secondRawTemplateBody = '# second stage\nResources:\n  Second: {}\n';
    const secondParsedTemplate = [{ Resources: { Second: {} } }];
    const { updateStageField, wrapper } = mountEditor(firstStage);

    wrapper.find(YamlEditor).prop('onChange')(firstRawTemplateBody, [{ Resources: { First: {} } }]);
    updateStageField.calls.reset();
    wrapper.setProps({
      stage: {
        type: 'deployCloudFormation',
        ...firstStage,
        refId: '2',
        templateBody: secondRawTemplateBody,
      },
    });

    expect(wrapper.find(YamlEditor).prop('value')).toBe(secondRawTemplateBody);
    expect(updateStageField).not.toHaveBeenCalled();

    wrapper.find(YamlEditor).prop('onChange')(secondRawTemplateBody, secondParsedTemplate);

    expect(updateStageField.calls.allArgs()).toEqual([[{ templateBody: secondParsedTemplate }]]);
    wrapper.unmount();
  });

  it('updates parameters, tags, and capabilities without coercing their values', () => {
    const parameters = '${parameters.cloudFormationParameters}';
    const tags = { Team: 'payments' };
    const capabilities = ['CAPABILITY_NAMED_IAM'];
    const { updateStageField, wrapper } = renderEditor({ parameters, tags, capabilities });

    expect(wrapper.find(MapEditor).length).toBe(2);
    if (wrapper.find(MapEditor).length !== 2) {
      return;
    }
    wrapper.find(MapEditor).at(0).prop('onChange')(parameters, false);
    wrapper.find(MapEditor).at(1).prop('onChange')(tags, false);
    wrapper.find(ReactSelectInput).prop('onChange')({ target: { value: capabilities } } as any);

    expect(updateStageField.calls.allArgs()).toEqual([[{ parameters }], [{ tags }], [{ capabilities }]]);
  });

  it('renders and updates an artifact template reference', () => {
    const stackArtifact = { type: 's3/object', reference: 's3://bucket/template.yml' };
    const { updateStageField, wrapper } = renderEditor({
      source: 'artifact',
      stackArtifactId: 'expected-artifact-id',
      stackArtifact,
    });
    const selector = wrapper.find(StageArtifactSelectorDelegate);

    expect(selector.exists()).toBe(true);
    if (!selector.exists()) {
      return;
    }
    expect(selector.prop('expectedArtifactId')).toBe('expected-artifact-id');
    expect(selector.prop('artifact')).toBe(stackArtifact);
    selector.prop('onExpectedArtifactSelected')({
      id: 'replacement-id',
      matchArtifact: { artifactAccount: 'artifact-account' },
    } as any);
    selector.prop('onArtifactEdited')({ type: 'http/file', reference: 'https://example.test/template.yml' } as any);

    expect(updateStageField.calls.allArgs()).toEqual([
      [{ stackArtifactId: 'replacement-id', stackArtifactAccount: 'artifact-account', stackArtifact: null }],
      [
        {
          stackArtifactId: null,
          stackArtifact: { type: 'http/file', reference: 'https://example.test/template.yml' },
        },
      ],
    ]);
  });

  it('writes the artifact execution contract and clears it when switching to text', () => {
    const { updateStageField, wrapper } = renderEditor({ source: 'artifact' });
    const selector = wrapper.find(StageArtifactSelectorDelegate);

    expect(selector.exists()).toBe(true);
    if (!selector.exists()) {
      return;
    }
    selector.prop('onExpectedArtifactSelected')({
      id: 'expected-artifact-id',
      matchArtifact: { artifactAccount: 'artifact-account' },
    } as any);
    wrapper.find('input[name="source"][value="text"]').simulate('change');

    expect(updateStageField.calls.allArgs()).toEqual([
      [
        {
          stackArtifactId: 'expected-artifact-id',
          stackArtifactAccount: 'artifact-account',
          stackArtifact: null,
        },
      ],
      [
        {
          source: 'text',
          stackArtifactId: null,
          stackArtifactAccount: null,
          stackArtifact: null,
        },
      ],
    ]);
  });

  it('passes the stage update contract to change-set settings', () => {
    const { updateStageField, wrapper } = renderEditor({ isChangeSet: true, changeSetName: 'existing-change-set' });

    expect(wrapper.find(CloudFormationChangeSetInfo).exists()).toBe(true);
    if (!wrapper.find(CloudFormationChangeSetInfo).exists()) {
      return;
    }
    expect(wrapper.find(CloudFormationChangeSetInfo).props()).toEqual(
      jasmine.objectContaining({
        stage: jasmine.objectContaining({ changeSetName: 'existing-change-set' }),
        updateStageField,
      }),
    );
  });

  it('updates change-set settings through the direct stage contract', () => {
    const updateStageField = jasmine.createSpy('updateStageField');
    const wrapper = shallow(
      <CloudFormationChangeSetInfo
        {...({
          stage: {
            changeSetName: 'existing-change-set',
            executeChangeSet: true,
            actionOnReplacement: 'ask',
          },
          updateStageField,
        } as any)}
      />,
    );

    expect(wrapper.find(TextInput).prop('value')).toBe('existing-change-set');
    expect(
      wrapper
        .find(StageConfigField)
        .filterWhere((field) => field.prop('label') === 'If ChangeSet contains a replacement')
        .prop('helpKey'),
    ).toBe('aws.cloudformation.changeSet.options');
    let contractError: Error;
    try {
      wrapper.find(TextInput).prop('onChange')({ target: { value: 'new-change-set' } } as any);
      wrapper.find(CheckboxInput).prop('onChange')({ target: { checked: false } } as any);
      wrapper.find(ReactSelectInput).prop('onChange')({ target: { value: 'skip' } } as any);
    } catch (error) {
      contractError = error;
    }
    expect(contractError).toBeUndefined();
    if (contractError) {
      return;
    }
    expect(updateStageField.calls.allArgs()).toEqual([
      [{ changeSetName: 'new-change-set' }],
      [{ executeChangeSet: false }],
      [{ actionOnReplacement: 'skip' }],
    ]);
  });
});
