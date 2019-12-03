import React from 'react';
import Select, { Option } from 'react-select';
import { get } from 'lodash';

import {
  ArtifactTypePatterns,
  IArtifact,
  IExpectedArtifact,
  IFormikStageConfigInjectedProps,
  IPipeline,
  PreRewriteStageArtifactSelector,
  RadioButtonInput,
  SETTINGS,
  StageArtifactSelector,
  StageConfigField,
  yamlDocumentsToString,
  YamlEditor,
  TextInput,
  IGcbTrigger,
} from '@spinnaker/core';

export enum buildDefinitionSources {
  ARTIFACT = 'artifact',
  TEXT = 'text',
  TRIGGER = 'trigger',
}

export enum triggerType {
  BRANCH = 'branchName',
  TAG = 'tagName',
  COMMIT = 'commitSha',
}

interface IGoogleCloudBuildStageFormProps {
  googleCloudBuildAccounts: string[];
  gcbTriggers: IGcbTrigger[];
  updatePipeline: (pipeline: IPipeline) => void;
  fetchGcbTriggers: (account: string) => void;
}

interface IGoogleCloudBuildStageFormState {
  rawBuildDefinitionYaml: string;
}

export class GoogleCloudBuildStageForm extends React.Component<
  IGoogleCloudBuildStageFormProps & IFormikStageConfigInjectedProps,
  IGoogleCloudBuildStageFormState
> {
  public constructor(props: IGoogleCloudBuildStageFormProps & IFormikStageConfigInjectedProps) {
    super(props);
    const stage = props.formik.values;
    this.state = {
      rawBuildDefinitionYaml: stage.buildDefinition ? yamlDocumentsToString([stage.buildDefinition]) : '',
    };
  }

  private onYamlChange = (rawYaml: string, yamlDocs: any): void => {
    this.setState({ rawBuildDefinitionYaml: rawYaml });
    const buildDefinition = Array.isArray(yamlDocs) && yamlDocs.length > 0 ? yamlDocs[0] : null;
    this.props.formik.setFieldValue('buildDefinition', buildDefinition);
  };

  private onAccountChange = (accountOption: Option<string>) => {
    const account = accountOption.value;
    this.props.formik.setFieldValue('account', account);
    this.props.fetchGcbTriggers(account);
  };

  private getAccountOptions = (): Array<Option<string>> => {
    return this.props.googleCloudBuildAccounts.map(account => ({
      label: account,
      value: account,
    }));
  };

  private getCloudBuildTriggers = (): Array<Option<string>> => {
    return this.props.gcbTriggers.map(trigger => ({
      label: trigger.name,
      value: trigger.id,
    }));
  };

  private getTriggerTypes = (): Array<Option<string>> => {
    return [
      { value: triggerType.BRANCH, label: 'Branch name' },
      { value: triggerType.TAG, label: 'Tag Name' },
      { value: triggerType.COMMIT, label: 'Commit SHA' },
    ];
  };

  private getSourceOptions = (): Array<Option<string>> => {
    return [
      { value: buildDefinitionSources.TEXT, label: 'Text' },
      { value: buildDefinitionSources.ARTIFACT, label: 'Artifact' },
      { value: buildDefinitionSources.TRIGGER, label: 'Trigger' },
    ];
  };

  private onSourceChange = (e: any): void => {
    this.props.formik.setFieldValue('buildDefinitionSource', e.target.value);
  };

  private onTriggerValueChange = (e: React.ChangeEvent<HTMLInputElement>): void => {
    const stage = this.props.formik.values;
    if (stage.triggerType) {
      const path = `repoSource.${stage.triggerType}`;
      this.props.formik.setFieldValue(path, e.target.value);
    }
  };

  private onTriggerChange = (selectedTrigger: Option) => {
    this.props.formik.setFieldValue('triggerId', selectedTrigger.value);
  };

  private onTriggerTypeChange = (selectedType: Option) => {
    this.props.formik.setFieldValue('triggerType', selectedType.value);
    this.props.formik.setFieldValue('repoSource', null);
  };

  private getTriggerValue = (): string => {
    const stage = this.props.formik.values;
    if (stage.triggerType) {
      const path = `repoSource.${stage.triggerType}`;
      return get(stage, path);
    }

    return '';
  };

  private setArtifactId = (expectedArtifactId: string): void => {
    this.props.formik.setFieldValue('buildDefinitionArtifact.artifactId', expectedArtifactId);
    this.props.formik.setFieldValue('buildDefinitionArtifact.artifact', null);
  };

  private setArtifact = (artifact: IArtifact): void => {
    this.props.formik.setFieldValue('buildDefinitionArtifact.artifact', artifact);
    this.props.formik.setFieldValue('buildDefinitionArtifact.artifactId', null);
  };

  private setArtifactAccount = (accountName: string): void => {
    this.props.formik.setFieldValue('buildDefinitionArtifact.artifactAccount', accountName);
  };

  public render() {
    const stage = this.props.formik.values;
    return (
      <>
        <StageConfigField label="Account">
          <Select
            clearable={false}
            onChange={this.onAccountChange}
            options={this.getAccountOptions()}
            value={stage.account}
          />
        </StageConfigField>
        <StageConfigField label="Build Definition Source">
          <RadioButtonInput
            options={this.getSourceOptions()}
            onChange={this.onSourceChange}
            value={stage.buildDefinitionSource}
          />
        </StageConfigField>
        {stage.buildDefinitionSource === buildDefinitionSources.TEXT && (
          <StageConfigField label="Build Definition">
            <YamlEditor value={this.state.rawBuildDefinitionYaml} onChange={this.onYamlChange} />
          </StageConfigField>
        )}
        {stage.buildDefinitionSource === buildDefinitionSources.ARTIFACT && SETTINGS.feature['artifactsRewrite'] && (
          <StageConfigField label="Build Definition Artifact">
            <StageArtifactSelector
              artifact={get(stage, 'buildDefinitionArtifact.artifact')}
              excludedArtifactTypePatterns={[
                ArtifactTypePatterns.DOCKER_IMAGE,
                ArtifactTypePatterns.KUBERNETES,
                ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
                ArtifactTypePatterns.EMBEDDED_BASE64,
              ]}
              expectedArtifactId={get(stage, 'buildDefinitionArtifact.artifactId')}
              onArtifactEdited={this.setArtifact}
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) => this.setArtifactId(artifact.id)}
              pipeline={this.props.pipeline}
              stage={stage}
            />
          </StageConfigField>
        )}
        {stage.buildDefinitionSource === buildDefinitionSources.ARTIFACT && !SETTINGS.feature['artifactsRewrite'] && (
          <PreRewriteStageArtifactSelector
            excludedArtifactTypePatterns={[
              ArtifactTypePatterns.DOCKER_IMAGE,
              ArtifactTypePatterns.KUBERNETES,
              ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
              ArtifactTypePatterns.EMBEDDED_BASE64,
            ]}
            pipeline={this.props.pipeline}
            selectedArtifactAccount={get(stage, 'buildDefinitionArtifact.artifactAccount')}
            selectedArtifactId={get(stage, 'buildDefinitionArtifact.artifactId')}
            setArtifactAccount={this.setArtifactAccount}
            setArtifactId={this.setArtifactId}
            stage={stage}
            updatePipeline={this.props.updatePipeline}
          />
        )}
        {stage.buildDefinitionSource === buildDefinitionSources.TRIGGER && (
          <>
            <StageConfigField label="Trigger Name">
              <Select
                clearable={false}
                onChange={this.onTriggerChange}
                options={this.getCloudBuildTriggers()}
                value={stage.triggerId}
              />
            </StageConfigField>
            <StageConfigField label="Trigger Type">
              <Select
                clearable={false}
                onChange={this.onTriggerTypeChange}
                options={this.getTriggerTypes()}
                value={stage.triggerType}
              />
            </StageConfigField>
            <StageConfigField label="Value">
              <TextInput
                type="text"
                className="form-control"
                onChange={this.onTriggerValueChange}
                value={this.getTriggerValue()}
              />
            </StageConfigField>
          </>
        )}
      </>
    );
  }
}
