import { capitalize, get, isEmpty, map } from 'lodash';
import React from 'react';
import { Option } from 'react-select';

import {
  ArtifactTypePatterns,
  IArtifact,
  IExpectedArtifact,
  IFormikStageConfigInjectedProps,
  RadioButtonInput,
  StageArtifactSelectorDelegate,
  StageConfigField,
  yamlDocumentsToString,
  YamlEditor,
} from '@spinnaker/core';

import { PatchManifestOptionsForm } from './PatchManifestOptionsForm';
import { ManifestBindArtifactsSelector } from '../deployManifest/ManifestBindArtifactsSelector';
import { IManifestBindArtifact } from '../deployManifest/ManifestBindArtifactsSelector';
import { ManifestSource } from '../../../manifest/ManifestSource';
import { SelectorMode } from '../../../manifest/selector/IManifestSelector';
import { ManifestSelector } from '../../../manifest/selector/ManifestSelector';

interface IPatchManifestStageConfigFormProps {
  stageFieldUpdated: () => void;
}

interface IPatchManifestStageConfigFormState {
  rawManifest: string;
}

export class PatchManifestStageForm extends React.Component<
  IPatchManifestStageConfigFormProps & IFormikStageConfigInjectedProps,
  IPatchManifestStageConfigFormState
> {
  private readonly excludedManifestArtifactTypes = [
    ArtifactTypePatterns.DOCKER_IMAGE,
    ArtifactTypePatterns.KUBERNETES,
    ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
    ArtifactTypePatterns.EMBEDDED_BASE64,
    ArtifactTypePatterns.MAVEN_FILE,
  ];

  public constructor(props: IPatchManifestStageConfigFormProps & IFormikStageConfigInjectedProps) {
    super(props);
    const patchBody: any[] = get(props.formik.values, 'patchBody');
    const isTextManifest: boolean = get(props.formik.values, 'source') === ManifestSource.TEXT;
    this.state = {
      rawManifest: !isEmpty(patchBody) && isTextManifest ? yamlDocumentsToString(patchBody) : '',
    };
  }

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

  private handleRawManifestChange = (rawManifest: string, manifests: any): void => {
    this.setState({
      rawManifest,
    });
    this.props.formik.setFieldValue('patchBody', manifests);
  };

  private onManifestSelectorChange = (): void => {
    this.props.stageFieldUpdated();
  };

  private getSourceOptions = (): Array<Option<string>> => {
    return map([ManifestSource.TEXT, ManifestSource.ARTIFACT], (option) => ({
      label: capitalize(option),
      value: option,
    }));
  };

  public render() {
    const stage = this.props.formik.values;
    return (
      <div className="container-fluid form-horizontal">
        <h4>Resource to Patch</h4>
        <ManifestSelector
          application={this.props.application}
          modes={[SelectorMode.Static, SelectorMode.Dynamic]}
          onChange={this.onManifestSelectorChange}
          selector={stage as any}
        />
        <hr />
        <h4>Patch Content</h4>
        <StageConfigField label="Manifest Source" helpKey="kubernetes.manifest.source">
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
              helpKey="kubernetes.manifest.expectedArtifact"
              label="Manifest Artifact"
              onArtifactEdited={this.onManifestArtifactEdited}
              onExpectedArtifactSelected={(artifact: IExpectedArtifact) => this.onManifestArtifactSelected(artifact.id)}
              pipeline={this.props.pipeline}
              stage={stage}
            />
          </>
        )}
        <StageConfigField label="Required Artifacts to Bind" helpKey="kubernetes.manifest.requiredArtifactsToBind">
          <ManifestBindArtifactsSelector
            bindings={this.getRequiredArtifacts()}
            onChangeBindings={this.onRequiredArtifactsChanged}
            pipeline={this.props.pipeline}
            stage={stage}
          />
        </StageConfigField>

        <hr />
        <h4>Patch Options</h4>
        <PatchManifestOptionsForm
          strategy={!!stage.options && stage.options.mergeStrategy}
          onStrategyChange={(strategy: string) => this.props.formik.setFieldValue('options.mergeStrategy', strategy)}
          record={!!stage.options && stage.options.record}
          onRecordChange={(record: boolean) => this.props.formik.setFieldValue('options.record', record)}
        />
      </div>
    );
  }
}
