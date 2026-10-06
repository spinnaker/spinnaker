import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import { AccountService, DeckRuntimeContext, Registry } from '@spinnaker/core';
import { getFormGroupByLabel } from '../../../../../core/src/utils/testUtils/rtl';

import { AmazonStageConfig } from '../AmazonStageConfig';
import { CloudFormationChangeSetInfo } from './CloudFormationChangeSetInfo';
import { DeployCloudFormationStackStageConfig } from './DeployCloudFormationStackStageConfig';
import { registerDeployCloudFormationStackStage } from './deployCloudFormationStackStage';

vi.mock('@spinnaker/core', async (importOriginal) => {
  const actual = await importOriginal<any>();
  const ReactModule = await import('react');

  return {
    ...actual,
    YamlEditor: ({ ariaLabel, onChange, value }: any) =>
      ReactModule.createElement('textarea', {
        'aria-label': ariaLabel,
        onChange: (event: React.ChangeEvent<HTMLTextAreaElement>) =>
          onChange(event.target.value, actual.yamlStringToDocuments(event.target.value)),
        value: value ?? '',
      }),
  };
});

describe('Deploy CloudFormation stack stage', () => {
  beforeEach(() => {
    vi.spyOn(AccountService, 'getAllAccountDetailsForProvider').mockResolvedValue([]);
    vi.spyOn(AccountService, 'getArtifactAccounts').mockResolvedValue([]);
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue([]);
  });

  function renderEditor(stageOverrides: any = {}, application: any = {}, pipelineOverrides: any = {}) {
    const initialStage = { type: 'deployCloudFormation', ...stageOverrides };
    const updateStageField = vi.fn();
    let replaceStage: React.Dispatch<React.SetStateAction<any>>;
    function StageHarness() {
      const [stage, setStage] = React.useState(initialStage);
      replaceStage = setStage;
      const update = (changes: any) => {
        updateStageField(changes);
        setStage((current: any) => ({ ...current, ...changes }));
      };
      return (
        <DeployCloudFormationStackStageConfig
          application={application}
          pipeline={{ expectedArtifacts: [], ...pipelineOverrides } as any}
          stage={stage}
          updateStageField={update}
        />
      );
    }
    return {
      initialStage,
      replaceStage: (stage: any) => act(() => replaceStage(stage)),
      updateStageField,
      ...render(
        <DeckRuntimeContext.Provider value={{ services: { executionService: {} } } as any}>
          <StageHarness />
        </DeckRuntimeContext.Provider>,
      ),
    };
  }

  it('registers a dedicated stage editor', () => {
    const registerStage = vi.spyOn(Registry.pipeline, 'registerStage').mockReturnValue(undefined);
    registerDeployCloudFormationStackStage();
    expect(registerStage.mock.lastCall[0].component).not.toBe(AmazonStageConfig);
  });

  it('renders all text-template settings without changing persisted values on mount', async () => {
    const parameters = { Environment: 'production' };
    const tags = { Team: 'payments' };
    const capabilities = ['CAPABILITY_IAM', 'CAPABILITY_AUTO_EXPAND'];
    const rendered = renderEditor({
      account: 'test-account',
      cloudProvider: 'aws',
      credentials: 'test-account',
      regions: ['eu-west-1'],
      stackName: 'payment-stack',
      roleARN: 'arn:aws:iam::123456789012:role/cloudformation',
      source: 'text',
      templateBody: 'Resources:\n  Queue:\n    Type: AWS::SQS::Queue',
      parameters,
      tags,
      capabilities,
    });

    await screen.findByRole('option', { name: 'test-account' });
    expect(within(getFormGroupByLabel('Account')).getByRole('combobox')).toHaveValue('test-account');
    expect(screen.getByRole('checkbox', { name: 'eu-west-1' })).toBeChecked();
    expect(within(getFormGroupByLabel('Stack name')).getByRole('textbox')).toHaveValue('payment-stack');
    expect(within(getFormGroupByLabel('IAM role ARN')).getByRole('textbox')).toHaveValue(
      'arn:aws:iam::123456789012:role/cloudformation',
    );
    expect(screen.getByRole('radio', { name: 'Text' })).toBeChecked();
    expect(within(getFormGroupByLabel('Parameters')).getByLabelText('Key')).toHaveValue('Environment');
    expect(within(getFormGroupByLabel('Tags')).getByLabelText('Key')).toHaveValue('Team');
    capabilities.forEach((capability) => expect(screen.getByText(capability)).toBeInTheDocument());
    expect(rendered.updateStageField).not.toHaveBeenCalled();
  });

  it('initializes only missing required fields for a new stage', () => {
    const rendered = renderEditor(
      {},
      { defaultCredentials: { aws: 'default-account' }, defaultRegions: { aws: 'eu-west-1' } },
    );

    expect(rendered.updateStageField.mock.calls).toEqual([
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
    const rendered = renderEditor(
      {
        account: 'persisted-account',
        capabilities: [],
        cloudProvider: 'persisted-provider',
        credentials: 'persisted-credentials',
        parameters: {},
        regions: [],
        source: 'artifact',
        tags: {},
      },
      { defaultCredentials: { aws: 'default-account' }, defaultRegions: { aws: 'eu-west-1' } },
    );

    expect(rendered.updateStageField).not.toHaveBeenCalled();
    expect(screen.getByRole('radio', { name: 'Artifact' })).toBeChecked();
    expect(getFormGroupByLabel('Expected Artifact')).toBeInTheDocument();
  });

  it('updates stack identity, account, regions, and source through public controls', async () => {
    vi.spyOn(AccountService, 'getAllAccountDetailsForProvider').mockResolvedValue([{ name: 'other-account' }] as any);
    vi.spyOn(AccountService, 'getUniqueAttributeForAllAccounts').mockResolvedValue(['us-east-1']);
    const rendered = renderEditor({
      account: 'test-account',
      capabilities: [],
      cloudProvider: 'aws',
      credentials: 'test-account',
      parameters: {},
      regions: [],
      source: 'text',
      tags: {},
    });
    await screen.findByRole('option', { name: 'other-account' });
    rendered.updateStageField.mockClear();

    fireEvent.change(within(getFormGroupByLabel('Account')).getByRole('combobox'), {
      target: { value: 'other-account' },
    });
    fireEvent.click(await screen.findByRole('checkbox', { name: 'us-east-1' }));
    fireEvent.change(within(getFormGroupByLabel('Stack name')).getByRole('textbox'), {
      target: { value: 'other-stack' },
    });
    fireEvent.change(within(getFormGroupByLabel('IAM role ARN')).getByRole('textbox'), {
      target: { value: 'other-role' },
    });
    fireEvent.click(screen.getByRole('checkbox', { name: 'Create CloudFormation ChangeSet' }));
    fireEvent.click(screen.getByRole('radio', { name: 'Artifact' }));

    [
      [{ credentials: 'other-account', account: 'other-account' }],
      [{ regions: ['us-east-1'] }],
      [{ stackName: 'other-stack' }],
      [{ roleARN: 'other-role' }],
      [{ isChangeSet: true }],
      [{ source: 'artifact' }],
    ].forEach((call) => expect(rendered.updateStageField.mock.calls).toContainEqual(call));
  });

  it('round-trips parameters and tags without coercing values', () => {
    const rendered = renderEditor({
      capabilities: [],
      cloudProvider: 'aws',
      parameters: { Environment: 'production' },
      regions: [],
      source: 'text',
      tags: { Team: 'payments' },
    });
    rendered.updateStageField.mockClear();

    fireEvent.input(within(getFormGroupByLabel('Parameters')).getAllByRole('textbox')[1], {
      target: { value: '${ parameters.environment }' },
    });
    fireEvent.input(within(getFormGroupByLabel('Tags')).getAllByRole('textbox')[1], {
      target: { value: 'platform' },
    });

    expect(rendered.updateStageField.mock.calls).toEqual([
      [{ parameters: { Environment: '${ parameters.environment }' } }],
      [{ tags: { Team: 'platform' } }],
    ]);
  });

  it('writes and clears the artifact execution contract when switching sources', () => {
    const rendered = renderEditor({
      capabilities: [],
      cloudProvider: 'aws',
      parameters: {},
      regions: [],
      source: 'artifact',
      stackArtifactId: 'expected-artifact-id',
      stackArtifactAccount: 'artifact-account',
      stackArtifact: { type: 's3/object', reference: 's3://bucket/template.yml' },
      tags: {},
    });
    rendered.updateStageField.mockClear();

    expect(getFormGroupByLabel('Expected Artifact')).toHaveTextContent('Expected Artifact');
    fireEvent.click(screen.getByRole('radio', { name: 'Text' }));

    expect(rendered.updateStageField).toHaveBeenCalledWith({
      source: 'text',
      stackArtifactId: null,
      stackArtifactAccount: null,
      stackArtifact: null,
    });
  });

  it('updates change-set name and execution through the controlled form', () => {
    const updateStageField = vi.fn();
    render(
      <CloudFormationChangeSetInfo
        stage={{ changeSetName: 'existing-change-set', executeChangeSet: true, actionOnReplacement: 'ask' } as any}
        updateStageField={updateStageField}
      />,
    );

    fireEvent.change(within(getFormGroupByLabel('ChangeSet Name')).getByRole('textbox'), {
      target: { value: 'new-change-set' },
    });
    fireEvent.keyDown(within(getFormGroupByLabel('If ChangeSet contains a replacement')).getByRole('combobox'), {
      key: 'ArrowDown',
      keyCode: 40,
    });
    fireEvent.mouseDown(screen.getByText('skip it'));
    fireEvent.click(within(getFormGroupByLabel('Execute ChangeSet')).getByRole('checkbox'));

    expect(updateStageField.mock.calls).toEqual([
      [{ changeSetName: 'new-change-set' }],
      [{ actionOnReplacement: 'skip' }],
      [{ executeChangeSet: false }],
    ]);
  });

  it('retains valid raw YAML formatting across parent updates', () => {
    const parsedTemplate = [{ Resources: { Queue: { Type: 'AWS::SQS::Queue' } } }];
    const rawTemplateBody = '# queue template\nResources:\n  Queue: { Type: AWS::SQS::Queue }\n';
    const rendered = renderEditor({
      capabilities: [],
      cloudProvider: 'aws',
      parameters: {},
      refId: '1',
      regions: [],
      source: 'text',
      tags: {},
      templateBody: [{ Resources: {} }],
    });
    const editor = screen.getByRole('textbox', { name: 'CloudFormation template YAML' });

    fireEvent.change(editor, { target: { value: rawTemplateBody } });
    expect(rendered.updateStageField).toHaveBeenCalledWith({ templateBody: parsedTemplate });
    rendered.replaceStage({ ...rendered.initialStage, templateBody: parsedTemplate });

    expect(screen.getByRole('textbox', { name: 'CloudFormation template YAML' })).toHaveValue(rawTemplateBody);
  });

  it('retains invalid raw YAML without changing the persisted template', () => {
    const rawTemplateBody = '# incomplete edit\nResources: [';
    const rendered = renderEditor({
      capabilities: [],
      cloudProvider: 'aws',
      parameters: {},
      refId: '1',
      regions: [],
      source: 'text',
      tags: {},
      templateBody: [{ Resources: {} }],
    });

    fireEvent.change(screen.getByRole('textbox', { name: 'CloudFormation template YAML' }), {
      target: { value: rawTemplateBody },
    });
    rendered.replaceStage({ ...rendered.initialStage });

    expect(rendered.updateStageField).not.toHaveBeenCalled();
    expect(screen.getByRole('textbox', { name: 'CloudFormation template YAML' })).toHaveValue(rawTemplateBody);
  });

  it('resets raw YAML when refId changes without updating the new stage', () => {
    const rendered = renderEditor({
      capabilities: [],
      cloudProvider: 'aws',
      parameters: {},
      refId: 'stage-a',
      regions: [],
      source: 'text',
      tags: {},
      templateBody: [{ Resources: {} }],
    });
    fireEvent.change(screen.getByRole('textbox', { name: 'CloudFormation template YAML' }), {
      target: { value: '# first stage\nResources:\n  First: {}\n' },
    });
    rendered.updateStageField.mockClear();

    const secondRawTemplateBody = '# second stage\nResources:\n  Second: {}\n';
    rendered.replaceStage({ ...rendered.initialStage, refId: 'stage-b', templateBody: secondRawTemplateBody });

    expect(screen.getByRole('textbox', { name: 'CloudFormation template YAML' })).toHaveValue(secondRawTemplateBody);
    expect(rendered.updateStageField).not.toHaveBeenCalled();
  });

  it('selects an expected artifact through the public selector', () => {
    const expectedArtifact = {
      displayName: 'Pipeline template',
      id: 'replacement-id',
      matchArtifact: { artifactAccount: 'artifact-account', type: 's3/object' },
    };
    const rendered = renderEditor(
      {
        capabilities: [],
        cloudProvider: 'aws',
        parameters: {},
        regions: [],
        source: 'artifact',
        tags: {},
      },
      {},
      { expectedArtifacts: [expectedArtifact] },
    );
    rendered.updateStageField.mockClear();

    const selector = within(getFormGroupByLabel('Expected Artifact')).getByRole('combobox');
    fireEvent.keyDown(selector, { key: 'ArrowDown', keyCode: 40 });
    fireEvent.mouseDown(screen.getByText('Pipeline template'));

    expect(rendered.updateStageField).toHaveBeenCalledWith({
      stackArtifactId: 'replacement-id',
      stackArtifactAccount: 'artifact-account',
      stackArtifact: null,
    });
  });

  it('starts editing an inline artifact through the public selector', () => {
    const rendered = renderEditor({
      capabilities: [],
      cloudProvider: 'aws',
      parameters: {},
      regions: [],
      source: 'artifact',
      tags: {},
    });
    rendered.updateStageField.mockClear();

    const selector = within(getFormGroupByLabel('Expected Artifact')).getByRole('combobox');
    fireEvent.keyDown(selector, { key: 'ArrowDown', keyCode: 40 });
    fireEvent.mouseDown(screen.getByText('Define a new artifact...'));

    expect(rendered.updateStageField).toHaveBeenCalledWith({
      stackArtifactId: null,
      stackArtifact: expect.objectContaining({ customKind: true }),
    });
  });

  it('forwards the exact concrete inline artifact after editing its reference', async () => {
    vi.spyOn(AccountService, 'getArtifactAccounts').mockResolvedValue([
      { name: 'custom-artifact', types: ['custom/object'] },
    ]);
    const rendered = renderEditor({
      capabilities: [],
      cloudProvider: 'aws',
      parameters: {},
      regions: [],
      source: 'artifact',
      tags: {},
    });
    rendered.updateStageField.mockClear();

    const selector = within(getFormGroupByLabel('Expected Artifact')).getByRole('combobox');
    fireEvent.keyDown(selector, { key: 'ArrowDown', keyCode: 40 });
    fireEvent.mouseDown(screen.getByText('Define a new artifact...'));

    const expectedArtifactGroup = getFormGroupByLabel('Expected Artifact');
    await waitFor(() => getFormGroupByLabel('Reference', expectedArtifactGroup));
    fireEvent.change(within(getFormGroupByLabel('Type', expectedArtifactGroup)).getByRole('textbox'), {
      target: { value: 's3/object' },
    });
    const currentArtifact = rendered.updateStageField.mock.lastCall?.[0].stackArtifact;
    rendered.updateStageField.mockClear();
    fireEvent.change(within(getFormGroupByLabel('Reference', expectedArtifactGroup)).getByRole('textbox'), {
      target: { value: 's3://templates/production.yml' },
    });

    const expectedUpdate = {
      stackArtifactId: null,
      stackArtifact: {
        ...currentArtifact,
        artifactAccount: 'custom-artifact',
        customKind: true,
        reference: 's3://templates/production.yml',
        type: 's3/object',
      },
    };
    expect(rendered.updateStageField).toHaveBeenCalled();
    rendered.updateStageField.mock.calls.forEach(([update]) => expect(update).toStrictEqual(expectedUpdate));
  });

  it('updates capabilities through the public selector', () => {
    const rendered = renderEditor({
      capabilities: [],
      cloudProvider: 'aws',
      parameters: {},
      regions: [],
      source: 'text',
      tags: {},
    });
    rendered.updateStageField.mockClear();

    const selector = within(getFormGroupByLabel('Capabilities')).getByRole('combobox');
    fireEvent.keyDown(selector, { key: 'ArrowDown', keyCode: 40 });
    fireEvent.mouseDown(screen.getByText('CAPABILITY_NAMED_IAM'));

    expect(rendered.updateStageField).toHaveBeenCalledWith({ capabilities: ['CAPABILITY_NAMED_IAM'] });
  });
});
