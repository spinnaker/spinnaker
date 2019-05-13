import * as React from 'react';
import Select from 'react-select';

import {
  IStageConfigProps,
  AccountService,
  YamlEditor,
  yamlDocumentsToString,
  IAccount,
  StageArtifactSelector,
  SETTINGS,
  IExpectedArtifact,
  IArtifact,
  PreRewriteStageArtifactSelector,
  StageConfigField,
} from '@spinnaker/core';

import { ManifestBasicSettings } from 'kubernetes/v2/manifest/wizard/BasicSettings';

export interface IKubernetesRunJobStageConfigState {
  credentials: IAccount[];
  rawManifest?: string;
}

export class KubernetesV2RunJobStageConfig extends React.Component<IStageConfigProps> {
  public state: IKubernetesRunJobStageConfigState = {
    credentials: [],
  };

  public outputOptions = [
    { label: 'None', value: 'none' },
    { label: 'Logs', value: 'propertyFile' },
    { label: 'Artifact', value: 'artifact' },
  ];

  public accountChanged = (account: string) => {
    this.props.updateStageField({
      credentails: account,
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
    AccountService.getAllAccountDetailsForProvider('kubernetes', 'v2').then((accounts: any) => {
      this.setState({ credentials: accounts });
    });
    this.initRawManifest();
  }

  private sourceChanged = (event: any) => {
    this.props.updateStageField({ consumeArtifactSource: event.value });
  };

  private updateArtifactId(artifactId: string) {
    this.props.updateStageField({ consumeArtifactId: artifactId });
  }

  private updateArtifactAccount(artifactAccount: string) {
    this.props.updateStageField({ consumeArtifactAccount: artifactAccount });
  }

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

  private updatePropertyFile = (event: any) => {
    this.props.updateStageField({ propertyFile: event.target.value });
  };

  private checkFeatureFlag(flag: string): boolean {
    return !!SETTINGS.feature[flag];
  }

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

  public artifactRewriteForm() {
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

  public artifactForm() {
    const { stage, pipeline } = this.props;
    return (
      <PreRewriteStageArtifactSelector
        excludedArtifactTypes={[]}
        stage={stage}
        pipeline={pipeline}
        selectedArtifactId={stage.consumeArtifactId}
        selectedArtifactAccount={stage.consumeArtifactAccount}
        setArtifactAccount={(artifactAccount: string) => this.updateArtifactAccount(artifactAccount)}
        setArtifactId={(artifactId: string) => this.updateArtifactId(artifactId)}
      />
    );
  }

  public render() {
    const { application, stage } = this.props;

    let outputSource = <div />;
    if (stage.consumeArtifactSource === 'propertyFile') {
      outputSource = this.logSourceForm();
    } else if (stage.consumeArtifactSource === 'artifact') {
      outputSource = this.checkFeatureFlag('artifactsRewrite') ? this.artifactRewriteForm() : this.artifactForm();
    }

    return (
      <div className="container-fluid form-horizontal">
        <h4>Basic Settings</h4>
        <ManifestBasicSettings
          app={application}
          selectedAccount={stage.account || ''}
          accounts={this.state.credentials}
          onAccountSelect={(selectedAccount: string) => this.accountChanged(selectedAccount)}
        />
        <h4>Manifest Configuration</h4>
        <YamlEditor value={this.state.rawManifest} onChange={this.handleRawManifestChange} />
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
