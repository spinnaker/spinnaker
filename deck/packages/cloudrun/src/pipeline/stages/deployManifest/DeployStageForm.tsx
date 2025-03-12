import { capitalize, get, isEmpty, map } from 'lodash';
import React from 'react';
import type { Option } from 'react-select';

import type { IAccountDetails, IArtifact, IExpectedArtifact, IFormikStageConfigInjectedProps } from '@spinnaker/core';
import {
  ArtifactTypePatterns,
  CheckboxInput,
  RadioButtonInput,
  StageArtifactSelectorDelegate,
  StageConfigField,
  yamlDocumentsToString,
  YamlEditor,
} from '@spinnaker/core';

import type { IManifestBindArtifact } from './ManifestBindArtifactsSelector';
import { ManifestBindArtifactsSelector } from './ManifestBindArtifactsSelector';
import { ManifestSource } from '../../../manifest/ManifestSource';
import { ManifestBasicSettings } from '../../../manifest/wizard/BasicSettings';
import { ServerGroupNamePreview } from './serverGroupNamePreview';

interface IDeployManifestStageConfigFormProps {
  accounts: IAccountDetails[];
}

interface IDeployManifestStageConfigFormState {
  rawManifest: string;
  application: string;
  stack: string;
  details: string;
}

export class DeployStageForm extends React.Component<
  IDeployManifestStageConfigFormProps & IFormikStageConfigInjectedProps,
  IDeployManifestStageConfigFormState
> {
  private readonly excludedManifestArtifactTypes = [
    ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
    ArtifactTypePatterns.MAVEN_FILE,
  ];

  public constructor(props: IDeployManifestStageConfigFormProps & IFormikStageConfigInjectedProps) {
    super(props);
    const stage = this.props.formik.values;
    const manifests: any[] = get(props.formik.values, 'manifests');
    const isTextManifest: boolean = get(props.formik.values, 'source') === ManifestSource.TEXT;
    this.state = {
      rawManifest: !isEmpty(manifests) && isTextManifest ? yamlDocumentsToString(manifests) : '',
      application: this.props.application.name,
      stack: get(stage, 'stack'),
      details: get(stage, 'details'),
    };
  }

  private getSourceOptions = (): Array<Option<string>> => {
    return map([ManifestSource.TEXT, ManifestSource.ARTIFACT], (option) => ({
      label: capitalize(option),
      value: option,
    }));
  };

  private handleRawManifestChange = (rawManifest: string, manifests: any): void => {
    this.setState({
      rawManifest,
    });
    this.props.formik.setFieldValue('manifests', manifests);
  };

  private onManifestArtifactSelected = (expectedArtifactId: string): void => {
    this.props.formik.setFieldValue('manifestArtifactId', expectedArtifactId);
    this.props.formik.setFieldValue('manifestArtifact', null);
  };

  private onManifestArtifactEdited = (artifact: IArtifact) => {
    this.props.formik.setFieldValue('manifestArtifactId', null);
    this.props.formik.setFieldValue('manifestArtifact', artifact);
  };

  private getRequiredArtifacts = (): IManifestBindArtifact[] => {
    const { requiredArtifactIds, requiredArtifacts } = this.props.formik.values;
    return (requiredArtifactIds || [])
      .map((id: string) => ({ expectedArtifactId: id }))
      .concat(requiredArtifacts || []);
  };

  private onRequiredArtifactsChanged = (bindings: IManifestBindArtifact[]): void => {
    this.props.formik.setFieldValue(
      'requiredArtifactIds',
      bindings.filter((b) => b.expectedArtifactId).map((b) => b.expectedArtifactId),
    );
    this.props.formik.setFieldValue(
      'requiredArtifacts',
      bindings.filter((b) => b.artifact),
    );
  };

  public render() {
    const stage = this.props.formik.values;
    return (
      <div className="form-horizontal">
        <h4>Basic Settings</h4>
        <ManifestBasicSettings
          accounts={this.props.accounts}
          onAccountSelect={(accountName) => this.props.formik.setFieldValue('account', accountName)}
          selectedAccount={stage.account}
        />
        <StageConfigField label="Application">
          <input
            className="form-control input-sm"
            type="text"
            value={this.props.application.name}
            onChange={(event) => this.props.formik.setFieldValue('application', event.target.value)}
            disabled
          />
        </StageConfigField>
        <StageConfigField label="Stack">
          <input
            className="form-control input-sm"
            type="text"
            value={stage.stack}
            onChange={(event) => this.props.formik.setFieldValue('stack', event.target.value)}
          />
        </StageConfigField>
        <StageConfigField label="Details">
          <input
            className="form-control input-sm"
            type="text"
            value={stage.details}
            onChange={(event) => this.props.formik.setFieldValue('details', event.target.value)}
          />
        </StageConfigField>
        <ServerGroupNamePreview
          application={this.props.application.name}
          stack={stage.stack}
          details={stage.details}
        ></ServerGroupNamePreview>

        <hr />
        <h4>Manifest Configuration</h4>
        <StageConfigField label="Manifest Source" helpKey="cloudrun.manifest.source">
          <RadioButtonInput
            options={this.getSourceOptions()}
            onChange={(e: any) => this.props.formik.setFieldValue('source', e.target.value)}
            value={stage.source}
          />
        </StageConfigField>
        {stage.source === ManifestSource.TEXT && (
          <StageConfigField label="Manifest">
            <YamlEditor onChange={this.handleRawManifestChange} value={this.state.rawManifest} />
          </StageConfigField>
        )}
        {stage.source === ManifestSource.ARTIFACT && (
          <>
            <StageArtifactSelectorDelegate
              artifact={stage.manifestArtifact}
              excludedArtifactTypePatterns={this.excludedManifestArtifactTypes}
              expectedArtifactId={stage.manifestArtifactId}
              helpKey="cloudrun.manifest.expectedArtifact"
              label="Manifest Artifact"
              onArtifactEdited={this.onManifestArtifactEdited}
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) => this.onManifestArtifactSelected(artifact.id)}
              pipeline={this.props.pipeline}
              stage={stage}
            />
            <StageConfigField label="Expression Evaluation" helpKey="cloudrun.manifest.skipExpressionEvaluation">
              <CheckboxInput
                checked={stage.skipExpressionEvaluation === true}
                onChange={(e: any) => this.props.formik.setFieldValue('skipExpressionEvaluation', e.target.checked)}
                text="Skip SpEL expression evaluation"
              />
            </StageConfigField>
          </>
        )}
        <StageConfigField label="Required Artifacts to Bind" helpKey="cloudrun.manifest.requiredArtifactsToBind">
          <ManifestBindArtifactsSelector
            bindings={this.getRequiredArtifacts()}
            onChangeBindings={this.onRequiredArtifactsChanged}
            pipeline={this.props.pipeline}
            stage={stage}
          />
        </StageConfigField>
      </div>
    );
  }
}
