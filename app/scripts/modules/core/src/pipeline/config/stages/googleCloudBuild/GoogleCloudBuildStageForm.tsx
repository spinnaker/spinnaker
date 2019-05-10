import * as React from 'react';
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
} from '@spinnaker/core';

export enum buildDefinitionSources {
  ARTIFACT = 'artifact',
  TEXT = 'text',
}

interface IGoogleCloudBuildStageFormProps {
  googleCloudBuildAccounts: string[];
  updatePipeline: (pipeline: IPipeline) => void;
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

  private onAccountChange = (accountOption: Option) => {
    this.props.formik.setFieldValue('account', accountOption.value);
  };

  private getAccountOptions = (): Array<Option<string>> => {
    return this.props.googleCloudBuildAccounts.map(account => ({
      label: account,
      value: account,
    }));
  };

  private getSourceOptions = (): Array<Option<string>> => {
    return [
      { value: buildDefinitionSources.TEXT, label: 'Text' },
      { value: buildDefinitionSources.ARTIFACT, label: 'Artifact' },
    ];
  };

  private onSourceChange = (e: any): void => {
    this.props.formik.setFieldValue('buildDefinitionSource', e.target.value);
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
            excludedArtifactTypes={[
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
      </>
    );
  }
}
