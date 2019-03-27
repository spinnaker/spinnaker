import * as React from 'react';
import { Application, HelpField } from '@spinnaker/core';

import { IClusterConfig } from './configBin.reader';
import { ConfigBinModal } from './ConfigBinModal';
import { IMetricOption } from './metricOptions';

export interface IConfigBinLinkProps {
  application: Application;
  config: IClusterConfig;
  clusterName: string;
  awsAccountId: string;
  region: string;
  env: string;
  configUpdated: () => void;
  cannedMetrics: IMetricOption[];
}

export interface IConfigBinLinkState {
  modalOpen: boolean;
}

export class ConfigBinLink extends React.Component<IConfigBinLinkProps, IConfigBinLinkState> {
  constructor(props: IConfigBinLinkProps) {
    super(props);
    this.state = {
      modalOpen: false,
    };
  }

  private showModal = (): void => {
    this.setState({ modalOpen: true });
  };

  private hideModal = (): void => {
    this.props.configUpdated();
    this.setState({ modalOpen: false });
  };

  public render() {
    const { config, awsAccountId, env, region, clusterName, application, cannedMetrics } = this.props;
    return (
      <div>
        <a className="clickable" onClick={this.showModal}>
          Configure available metrics
        </a>{' '}
        <HelpField id="titus.configBin.metrics" />
        {this.state.modalOpen && (
          <ConfigBinModal
            application={application}
            config={config}
            awsAccountId={awsAccountId}
            env={env}
            region={region}
            showCallback={this.hideModal}
            clusterName={clusterName}
            cannedMetrics={cannedMetrics}
          />
        )}
      </div>
    );
  }
}
