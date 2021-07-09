import { capitalize, map } from 'lodash';
import React from 'react';
import Select, { Option } from 'react-select';

import {
  AccountService,
  ArtifactTypePatterns,
  IAccount,
  IArtifact,
  IExpectedArtifact,
  IStageConfigProps,
  RadioButtonInput,
  StageArtifactSelector,
  StageArtifactSelectorDelegate,
  StageConfigField,
  yamlDocumentsToString,
  YamlEditor,
} from '@spinnaker/core';

import { ManifestBindArtifactsSelector } from '../deployManifest/ManifestBindArtifactsSelector';
import { IManifestBindArtifact } from '../deployManifest/ManifestBindArtifactsSelector';
import { ManifestSource } from '../../../manifest/ManifestSource';
import { ManifestBasicSettings } from '../../../manifest/wizard/BasicSettings';

export interface IKubernetesRunJobStageConfigState {
  credentials: IAccount[];
  rawManifest?: string;
}

export class KubernetesV2RunJobStageConfig extends React.Component<IStageConfigProps> {
  public state: IKubernetesRunJobStageConfigState = {
    credentials: [],
  };

  private readonly excludedManifestArtifactTypes = [
    ArtifactTypePatterns.DOCKER_IMAGE,
    ArtifactTypePatterns.KUBERNETES,
    ArtifactTypePatterns.FRONT50_PIPELINE_TEMPLATE,
    ArtifactTypePatterns.MAVEN_FILE,
  ];

  constructor(props: IStageConfigProps) {
    super(props);
    const { stage, application } = this.props;
    if (!stage.application) {
      stage.application = application.name;
    }
    if (!stage.source) {
      stage.source = ManifestSource.TEXT;
    }
  }

  public outputOptions = [
    { label: 'None', value: 'none' },
    { label: 'Logs', value: 'propertyFile' },
    { label: 'Artifact', value: 'artifact' },
  ];

  public accountChanged = (account: string) => {
    this.props.updateStageField({
      credentials: account,
      account: account,
    });
  };

  public handleRawManifestChange = (rawManifest: string, manifests: any) => {
    if (manifests) {
      this.props.updateStageField({ manifest: manifests[0] });
    }
    this.setState({ rawManifest });
  };

  public initRawManifest() {
    const { stage } = this.props;
    if (stage.manifest) {
      this.setState({ rawManifest: yamlDocumentsToString([stage.manifest]) });
    }
  }

  public componentDidMount() {
    this.props.updateStageField({ cloudProvider: 'kubernetes' });
    AccountService.getAllAccountDetailsForProvider('kubernetes').then((accounts: any) => {
      this.setState({ credentials: accounts });
    });
    this.initRawManifest();
  }

  private sourceChanged = (event: any) => {
    this.props.updateStageField({ consumeArtifactSource: event.value });
    if (event.value === 'none') {
      this.props.updateStageField({ propertyFile: null });
    }
  };

  private onArtifactSelected = (artifact: IExpectedArtifact) => {
    this.props.updateStageField({ consumeArtifactId: artifact.id });
  };

  private onArtifactEdited = (artifact: IArtifact) => {
    this.props.updateStageField({
      consumeArtifact: artifact,
      consumeArtifactId: artifact.id,
      consumeArtifactAccount: artifact.artifactAccount,
    });
  };

  private onManifestArtifactSelected = (expectedArtifactId: string): void => {
    this.props.updateStageField({
      manifestArtifactId: expectedArtifactId,
      manifestArtifact: null,
    });
  };

  private onManifestArtifactEdited = (artifact: IArtifact) => {
    this.props.updateStageField({
      manifestArtifactId: null,
      manifestArtifact: artifact,
    });
  };

  private updatePropertyFile = (event: any) => {
    this.props.updateStageField({ propertyFile: event.target.value });
  };

  public logSourceForm() {
    const { stage } = this.props;
    return (
      <StageConfigField label="Container Name" helpKey="kubernetes.runJob.captureSource.containerName">
        <input
          className="form-control input-sm"
          type="text"
          value={stage.propertyFile}
          onChange={this.updatePropertyFile}
        />
      </StageConfigField>
    );
  }

  public artifactForm() {
    const { stage, pipeline } = this.props;
    return (
      <StageConfigField label="Artifact">
        <StageArtifactSelector
          pipeline={pipeline}
          stage={stage}
          artifact={stage.consumeArtifact}
          excludedArtifactTypePatterns={[]}
          expectedArtifactId={stage.consumeArtifactId}
          onExpectedArtifactSelected={this.onArtifactSelected}
          onArtifactEdited={this.onArtifactEdited}
        />
      </StageConfigField>
    );
  }

  private getSourceOptions = (): Array<Option<string>> => {
    return map([ManifestSource.TEXT, ManifestSource.ARTIFACT], (option) => ({
      label: capitalize(option),
      value: option,
    }));
  };

  private getRequiredArtifacts = (): IManifestBindArtifact[] => {
    const { requiredArtifactIds, requiredArtifacts } = this.props.stage;
    return (requiredArtifactIds || [])
      .map((id: string) => ({ expectedArtifactId: id }))
      .concat(requiredArtifacts || []);
  };

  private onRequiredArtifactsChanged = (bindings: IManifestBindArtifact[]): void => {
    this.props.updateStageField({
      requiredArtifactIds: bindings.filter((b) => b.expectedArtifactId).map((b) => b.expectedArtifactId),
    });
    this.props.updateStageField({ requiredArtifacts: bindings.filter((b) => b.artifact) });
  };

  public render() {
    const { stage } = this.props;

    let outputSource = <div />;
    if (stage.consumeArtifactSource === 'propertyFile') {
      outputSource = this.logSourceForm();
    } else if (stage.consumeArtifactSource === 'artifact') {
      outputSource = this.artifactForm();
    }

    return (
      <div className="container-fluid form-horizontal">
        <h4>Basic Settings</h4>
        <ManifestBasicSettings
          selectedAccount={stage.account || ''}
          accounts={this.state.credentials}
          onAccountSelect={(selectedAccount: string) => this.accountChanged(selectedAccount)}
        />
        <h4>Manifest Configuration</h4>
        <StageConfigField label="Manifest Source" helpKey="kubernetes.manifest.source">
          <RadioButtonInput
            options={this.getSourceOptions()}
            onChange={(e: any) => this.props.updateStageField({ source: e.target.value })}
            value={stage.source}
          />
        </StageConfigField>
        {stage.source === ManifestSource.TEXT && (
          <YamlEditor value={this.state.rawManifest} onChange={this.handleRawManifestChange} />
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
        <h4>Output</h4>
        <StageConfigField label="Capture Output From" helpKey="kubernetes.runJob.captureSource">
          <div>
            <Select
              clearable={false}
              options={this.outputOptions}
              value={stage.consumeArtifactSource}
              onChange={this.sourceChanged}
            />
          </div>
        </StageConfigField>
        {outputSource}
      </div>
    );
  }
}
