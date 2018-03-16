import * as React from 'react';
import { IClusterConfig } from './configBin.reader';
import { BindAll } from 'lodash-decorators';
import { ConfigBinModal } from './ConfigBinModal';
import { Application, HelpField } from '@spinnaker/core';

export interface IConfigBinLinkProps {
  application: Application;
  config: IClusterConfig;
  clusterName: string;
  awsAccountId: string;
  region: string;
  env: string;
  configUpdated: () => void;
  linkText?: string;
}

export interface IConfigBinLinkState {
  modalOpen: boolean;
}

@BindAll()
export class ConfigBinLink extends React.Component<IConfigBinLinkProps, IConfigBinLinkState> {

  constructor(props: IConfigBinLinkProps) {
    super(props);
    this.state = {
      modalOpen: false
    };
  }

  private showModal(): void {
    this.setState({ modalOpen: true });
  }

  private hideModal(): void {
    this.props.configUpdated();
    this.setState({ modalOpen: false });
  }

  public render() {
    const { config, awsAccountId, env, region, clusterName, application } = this.props;
    const linkText = this.props.linkText || 'Configure available metrics';
    return (
      <div>
        <a className="clickable" onClick={this.showModal}>{linkText}</a> <HelpField id="titus.configBin.metrics"/>

        {this.state.modalOpen && (
          <ConfigBinModal
            application={application}
            config={config}
            awsAccountId={awsAccountId}
            env={env}
            region={region}
            showCallback={this.hideModal}
            clusterName={clusterName}
          />
        )}
      </div>
    );
  }
}


