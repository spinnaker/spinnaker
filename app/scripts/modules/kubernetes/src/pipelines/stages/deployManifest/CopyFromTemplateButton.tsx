import React from 'react';

import { Application, IManifest } from '@spinnaker/core';

import { ManifestCopier } from './ManifestCopier';

export interface ICopyFromTemplateProps {
  application: Application;
  handleCopy(manifest: IManifest): void;
}

export interface ICopyFromTemplateState {
  show: boolean;
}

export class CopyFromTemplateButton extends React.Component<ICopyFromTemplateProps, ICopyFromTemplateState> {
  constructor(props: ICopyFromTemplateProps) {
    super(props);
    this.state = { show: false };
  }

  private toggle = () => this.setState({ show: !this.state.show });

  private handleManifestSelected = (manifest: IManifest) => {
    this.props.handleCopy(manifest);
    this.toggle();
  };

  public render() {
    return (
      <>
        <ManifestCopier
          show={this.state.show}
          application={this.props.application}
          cloudProvider="kubernetes"
          onDismiss={this.toggle}
          onManifestSelected={this.handleManifestSelected}
        />
        <button className="link" onClick={this.toggle} style={{ paddingLeft: 0 }}>
          Copy from running infrastructure
        </button>
      </>
    );
  }
}
