import * as React from 'react';
import Select, { Option } from 'react-select';
import { get } from 'lodash';

import {
  ArtifactTypePatterns,
  IArtifact,
  IExpectedArtifact,
  IFormikStageConfigInjectedProps,
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

  private onSourceChange = (e: any) => {
    this.props.formik.setFieldValue('buildDefinitionSource', e.target.value);
  };

  private onExpectedArtifactSelected = (expectedArtifact: IExpectedArtifact) => {
    this.props.formik.setFieldValue('buildDefinitionArtifact.artifactId', expectedArtifact.id);
    this.props.formik.setFieldValue('buildDefinitionArtifact.artifact', null);
  };

  private onArtifactEdited = (artifact: IArtifact) => {
    this.props.formik.setFieldValue('buildDefinitionArtifact.artifact', artifact);
    this.props.formik.setFieldValue('buildDefinitionArtifact.artifactId', null);
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
            value={this.props.formik.values.account}
          />
        </StageConfigField>
        {SETTINGS.feature['artifactsRewrite'] && (
          <StageConfigField label="Build Definition Source">
            <RadioButtonInput
              options={this.getSourceOptions()}
              onChange={this.onSourceChange}
              value={this.props.formik.values.buildDefinitionSource}
            />
          </StageConfigField>
        )}
        {this.props.formik.values.buildDefinitionSource === buildDefinitionSources.TEXT && (
          <StageConfigField label="Build Definition">
            <YamlEditor value={this.state.rawBuildDefinitionYaml} onChange={this.onYamlChange} />
          </StageConfigField>
        )}
        {this.props.formik.values.buildDefinitionSource === buildDefinitionSources.ARTIFACT &&
          SETTINGS.feature['artifactsRewrite'] && (
            <StageConfigField label="Build Definition Artifact">
              <StageArtifactSelector
                pipeline={this.props.pipeline}
                stage={stage}
                expectedArtifactId={get(stage, 'buildDefinitionArtifact.artifactId')}
                artifact={get(stage, 'buildDefinitionArtifact.artifact')}
                onExpectedArtifactSelected={this.onExpectedArtifactSelected}
                onArtifactEdited={this.onArtifactEdited}
                excludedArtifactTypePatterns={[
                  ArtifactTypePatterns.DOCKER_IMAGE,
                  ArtifactTypePatterns.KUBERNETES,
                  ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
                  ArtifactTypePatterns.EMBEDDED_BASE64,
                ]}
              />
            </StageConfigField>
          )}
      </>
    );
  }
}
