import React from 'react';

import type { IAccount, IArtifact, IExpectedArtifact, IStageConfigProps } from '@spinnaker/core';
import {
  AccountSelectInput,
  AccountService,
  ChecklistInput,
  MapEditor,
  ReactSelectInput,
  StageArtifactSelectorDelegate,
  StageConfigField,
  yamlDocumentsToString,
  YamlEditor,
} from '@spinnaker/core';

import { CloudFormationChangeSetInfo } from './CloudFormationChangeSetInfo';

const capabilities = ['CAPABILITY_IAM', 'CAPABILITY_NAMED_IAM', 'CAPABILITY_AUTO_EXPAND'];

function templateBodyAsYaml(templateBody: any): string {
  if (typeof templateBody === 'string') {
    return templateBody;
  }
  if (templateBody == null) {
    return '';
  }
  return yamlDocumentsToString(Array.isArray(templateBody) ? templateBody : [templateBody]);
}

export function DeployCloudFormationStackStageConfig({
  application,
  pipeline,
  stage,
  updateStageField,
}: IStageConfigProps) {
  const [accounts, setAccounts] = React.useState<IAccount[]>([]);
  const [regions, setRegions] = React.useState<string[]>([]);
  const [templateEditorState, setTemplateEditorState] = React.useState(() => ({
    refId: stage.refId,
    rawTemplateBody: templateBodyAsYaml(stage.templateBody),
  }));
  const source = stage.source || 'text';
  const selectedAccount = stage.credentials || stage.account || '';
  const accountNames = [selectedAccount, ...accounts.map((account) => account.name)].filter(
    (account, index, allAccounts) => account && allAccounts.indexOf(account) === index,
  );
  const regionNames = [...(stage.regions || []), ...regions].filter(
    (region, index, allRegions) => region && allRegions.indexOf(region) === index,
  );

  if (templateEditorState.refId !== stage.refId) {
    setTemplateEditorState({
      refId: stage.refId,
      rawTemplateBody: templateBodyAsYaml(stage.templateBody),
    });
  }

  React.useEffect(() => {
    const changes: Record<string, any> = {};
    const defaults: Record<string, any> = {
      capabilities: [],
      cloudProvider: 'aws',
      parameters: {},
      regions: application.defaultRegions?.aws ? [application.defaultRegions.aws] : [],
      source: 'text',
      tags: {},
    };

    Object.entries(defaults).forEach(([field, value]) => {
      if (stage[field] === undefined) {
        changes[field] = value;
      }
    });

    const defaultCredentials = application.defaultCredentials?.aws;
    if (defaultCredentials) {
      if (stage.credentials === undefined) {
        changes.credentials = defaultCredentials;
      }
      if (stage.account === undefined) {
        changes.account = defaultCredentials;
      }
    }

    if (Object.keys(changes).length) {
      updateStageField(changes);
    }
  }, []);

  React.useEffect(() => {
    let mounted = true;
    Promise.all([
      AccountService.getAllAccountDetailsForProvider('aws'),
      AccountService.getUniqueAttributeForAllAccounts('aws', 'regions'),
    ]).then(([loadedAccounts, loadedRegions]) => {
      if (mounted) {
        setAccounts(loadedAccounts);
        setRegions(loadedRegions);
      }
    });
    return () => {
      mounted = false;
    };
  }, []);

  const onStackArtifactEdited = (stackArtifact: IArtifact) => {
    updateStageField({ stackArtifactId: null, stackArtifact });
  };

  const onStackArtifactSelected = (expectedArtifact: IExpectedArtifact) => {
    updateStageField({
      stackArtifactId: expectedArtifact.id,
      stackArtifactAccount: expectedArtifact.matchArtifact?.artifactAccount || null,
      stackArtifact: null,
    });
  };

  return (
    <div className="container-fluid form-horizontal">
      <h4>Basic Settings</h4>
      <StageConfigField label="Account">
        <AccountSelectInput
          accounts={accountNames}
          name="credentials"
          onChange={(event: React.ChangeEvent<HTMLSelectElement>) =>
            updateStageField({ credentials: event.target.value, account: event.target.value })
          }
          provider="aws"
          value={selectedAccount}
        />
      </StageConfigField>
      <StageConfigField label="Regions">
        <ChecklistInput
          inline={true}
          name="regions"
          onChange={(event: React.ChangeEvent<HTMLInputElement>) => updateStageField({ regions: event.target.value })}
          showSelectAll={true}
          stringOptions={regionNames}
          value={stage.regions || []}
        />
      </StageConfigField>

      <h4>Template Configuration</h4>
      <StageConfigField label="Stack name">
        <input
          className="form-control input-sm"
          name="stackName"
          onChange={(event) => updateStageField({ stackName: event.target.value })}
          required={true}
          type="text"
          value={stage.stackName || ''}
        />
        <label>
          <input
            checked={!!stage.isChangeSet}
            name="isChangeSet"
            onChange={(event) => updateStageField({ isChangeSet: event.target.checked })}
            type="checkbox"
          />{' '}
          <strong>Create CloudFormation ChangeSet</strong>
        </label>
      </StageConfigField>
      <StageConfigField label="IAM role ARN">
        <input
          className="form-control input-sm"
          name="roleARN"
          onChange={(event) => updateStageField({ roleARN: event.target.value })}
          type="text"
          value={stage.roleARN || ''}
        />
      </StageConfigField>
      <StageConfigField label="Source" helpKey="aws.cloudformation.source">
        <label>
          <input
            checked={source === 'text'}
            name="source"
            onChange={() =>
              updateStageField({
                source: 'text',
                stackArtifactId: null,
                stackArtifactAccount: null,
                stackArtifact: null,
              })
            }
            type="radio"
            value="text"
          />{' '}
          Text
        </label>
        <br />
        <label>
          <input
            checked={source === 'artifact'}
            name="source"
            onChange={() => updateStageField({ source: 'artifact' })}
            type="radio"
            value="artifact"
          />{' '}
          Artifact
        </label>
      </StageConfigField>

      {source === 'text' && (
        <YamlEditor
          onChange={(raw, templateBody) => {
            setTemplateEditorState({ refId: stage.refId, rawTemplateBody: raw });
            if (templateBody !== null) {
              updateStageField({ templateBody });
            }
          }}
          value={templateEditorState.rawTemplateBody}
        />
      )}
      {source === 'artifact' && (
        <StageArtifactSelectorDelegate
          artifact={stage.stackArtifact}
          excludedArtifactTypePatterns={[]}
          expectedArtifactId={stage.stackArtifactId}
          fieldColumns={8}
          helpKey="aws.cloudformation.expectedArtifact"
          label="Expected Artifact"
          onArtifactEdited={onStackArtifactEdited}
          onExpectedArtifactSelected={onStackArtifactSelected}
          pipeline={pipeline}
          stage={stage}
        />
      )}
      {stage.isChangeSet && <CloudFormationChangeSetInfo stage={stage} updateStageField={updateStageField} />}

      <hr />
      <StageConfigField label="Parameters" fieldColumns={6}>
        <MapEditor
          addButtonLabel="Add parameter"
          model={stage.parameters ?? {}}
          onChange={(parameters) => updateStageField({ parameters })}
          pipeline={pipeline}
          valueCanContainSpel={true}
        />
      </StageConfigField>
      <hr />
      <StageConfigField label="Tags" fieldColumns={6}>
        <MapEditor
          addButtonLabel="Add tag"
          model={stage.tags ?? {}}
          onChange={(tags) => updateStageField({ tags })}
          pipeline={pipeline}
          valueCanContainSpel={true}
        />
      </StageConfigField>
      <hr />
      <StageConfigField label="Capabilities" fieldColumns={6}>
        <ReactSelectInput
          multi={true}
          name="capabilities"
          onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
            updateStageField({ capabilities: event.target.value })
          }
          stringOptions={capabilities}
          value={stage.capabilities}
        />
      </StageConfigField>
    </div>
  );
}
