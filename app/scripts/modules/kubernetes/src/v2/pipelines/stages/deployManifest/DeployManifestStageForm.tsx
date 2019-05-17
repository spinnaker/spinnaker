import * as React from 'react';
import { capitalize, get, isEmpty, map } from 'lodash';
import { Option } from 'react-select';

import {
  ArtifactTypePatterns,
  CheckboxInput,
  IAccountDetails,
  IArtifact,
  IExpectedArtifact,
  IFormikStageConfigInjectedProps,
  IManifest,
  IPipeline,
  RadioButtonInput,
  StageConfigField,
  StageArtifactSelectorDelegate,
  yamlDocumentsToString,
  YamlEditor,
} from '@spinnaker/core';

import { ManifestBasicSettings } from 'kubernetes/v2/manifest/wizard/BasicSettings';
import { CopyFromTemplateButton } from './CopyFromTemplateButton';
import { IManifestBindArtifact } from './ManifestBindArtifactsSelector';
import { ManifestDeploymentOptions } from './ManifestDeploymentOptions';
import { ManifestBindArtifactsSelectorDelegate } from './ManifestBindArtifactsSelectorDelegate';
import { NamespaceSelector } from './NamespaceSelector';

interface IDeployManifestStageConfigFormProps {
  accounts: IAccountDetails[];
  updatePipeline: (pipeline: IPipeline) => void;
}

interface IDeployManifestStageConfigFormState {
  rawManifest: string;
  overrideNamespace: boolean;
}

export class DeployManifestStageForm extends React.Component<
  IDeployManifestStageConfigFormProps & IFormikStageConfigInjectedProps,
  IDeployManifestStageConfigFormState
> {
  public readonly textSource = 'text';
  public readonly artifactSource = 'artifact';
  private readonly excludedManifestArtifactTypes = [
    ArtifactTypePatterns.DOCKER_IMAGE,
    ArtifactTypePatterns.KUBERNETES,
    ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
    ArtifactTypePatterns.EMBEDDED_BASE64,
  ];

  public constructor(props: IDeployManifestStageConfigFormProps & IFormikStageConfigInjectedProps) {
    super(props);
    const stage = this.props.formik.values;
    const manifests: any[] = get(props.formik.values, 'manifests');
    const isTextManifest: boolean = get(props.formik.values, 'source') === this.textSource;
    this.state = {
      rawManifest: !isEmpty(manifests) && isTextManifest ? yamlDocumentsToString(manifests) : '',
      overrideNamespace: get(stage, 'namespaceOverride', '') !== '',
    };
  }

  private getSourceOptions = (): Array<Option<string>> => {
    return map([this.textSource, this.artifactSource], option => ({
      label: capitalize(option),
      value: option,
    }));
  };

  private handleCopy = (manifest: IManifest): void => {
    this.props.formik.setFieldValue('manifests', [manifest]);
    this.setState({
      rawManifest: yamlDocumentsToString([manifest]),
    });
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

  private onManifestArtifactAccountSelected = (accountName: string): void => {
    this.props.formik.setFieldValue('manifestArtifactAccount', accountName);
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
      bindings.filter(b => b.expectedArtifactId).map(b => b.expectedArtifactId),
    );
    this.props.formik.setFieldValue('requiredArtifacts', bindings.filter(b => b.artifact));
  };

  private overrideNamespaceChange(checked: boolean) {
    if (!checked) {
      this.props.formik.setFieldValue('namespaceOverride', '');
    }
    this.setState({ overrideNamespace: checked });
  }

  public render() {
    const stage = this.props.formik.values;
    return (
      <div className="form-horizontal">
        <h4>Basic Settings</h4>
        <ManifestBasicSettings
          app={this.props.application}
          accounts={this.props.accounts}
          onAccountSelect={accountName => this.props.formik.setFieldValue('account', accountName)}
          selectedAccount={stage.account}
        />
        <StageConfigField label="Override Namespace">
          <CheckboxInput
            checked={this.state.overrideNamespace}
            onChange={(e: any) => this.overrideNamespaceChange(e.target.checked)}
          />
        </StageConfigField>
        {this.state.overrideNamespace && (
          <StageConfigField label="Namespace">
            <NamespaceSelector
              createable={true}
              accounts={this.props.accounts}
              selectedAccount={stage.account}
              selectedNamespace={stage.namespaceOverride || ''}
              onChange={namespace => this.props.formik.setFieldValue('namespaceOverride', namespace)}
            />
          </StageConfigField>
        )}
        <hr />
        <h4>Manifest Configuration</h4>
        <StageConfigField label="Manifest Source" helpKey="kubernetes.manifest.source">
          <RadioButtonInput
            options={this.getSourceOptions()}
            onChange={(e: any) => this.props.formik.setFieldValue('source', e.target.value)}
            value={stage.source}
          />
        </StageConfigField>
        {stage.source === this.textSource && (
          <StageConfigField label="Manifest">
            <CopyFromTemplateButton application={this.props.application} handleCopy={this.handleCopy} />
            <YamlEditor onChange={this.handleRawManifestChange} value={this.state.rawManifest} />
          </StageConfigField>
        )}
        {stage.source === this.artifactSource && (
          <>
            <StageArtifactSelectorDelegate
              artifact={stage.manifestArtifact}
              excludedArtifactTypePatterns={this.excludedManifestArtifactTypes}
              expectedArtifactId={stage.manifestArtifactId}
              helpKey="kubernetes.manifest.expectedArtifact"
              label="Manifest Artifact"
              onArtifactEdited={this.onManifestArtifactEdited}
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) => this.onManifestArtifactSelected(artifact.id)}
              pipeline={this.props.pipeline}
              selectedArtifactAccount={stage.manifestArtifactAccount}
              selectedArtifactId={stage.manifestArtifactId}
              setArtifactAccount={this.onManifestArtifactAccountSelected}
              setArtifactId={this.onManifestArtifactSelected}
              stage={stage}
              updatePipeline={this.props.updatePipeline}
            />
            <StageConfigField label="Expression Evaluation" helpKey="kubernetes.manifest.skipExpressionEvaluation">
              <CheckboxInput
                checked={stage.skipExpressionEvaluation === true}
                onChange={(e: any) => this.props.formik.setFieldValue('skipExpressionEvaluation', e.target.checked)}
                text="Skip SpEL expression evaluation"
              />
            </StageConfigField>
          </>
        )}
        <StageConfigField label="Required Artifacts to Bind" helpKey="kubernetes.manifest.requiredArtifactsToBind">
          <ManifestBindArtifactsSelectorDelegate
            bindings={this.getRequiredArtifacts()}
            onChangeBindings={this.onRequiredArtifactsChanged}
            pipeline={this.props.pipeline}
            stage={stage}
          />
        </StageConfigField>
        <hr />
        <ManifestDeploymentOptions
          accounts={this.props.accounts}
          config={stage.trafficManagement}
          onConfigChange={config => this.props.formik.setFieldValue('trafficManagement', config)}
          selectedAccount={stage.account}
        />
      </div>
    );
  }
}
