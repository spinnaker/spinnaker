import * as React from 'react';

import { IStageConfigProps, AccountService, YamlEditor, yamlDocumentsToString, IAccount } from '@spinnaker/core';

import { ManifestBasicSettings } from 'kubernetes/v2/manifest/wizard/BasicSettings';

export interface IKubernetesRunJobStageConfigState {
  credentials: IAccount[];
  rawManifest?: string;
}

export class KubernetesV2RunJobStageConfig extends React.Component<IStageConfigProps> {
  public state: IKubernetesRunJobStageConfigState = {
    credentials: [],
  };

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

  public render() {
    const { application, stage } = this.props;

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
      </div>
    );
  }
}
