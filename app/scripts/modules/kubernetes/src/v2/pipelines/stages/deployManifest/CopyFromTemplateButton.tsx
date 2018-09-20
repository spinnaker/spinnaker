import * as React from 'react';
import { module } from 'angular';
import { react2angular } from 'react2angular';
import { Application, IManifest } from '@spinnaker/core';

import { ManifestCopier } from './ManifestCopier';

export interface ICopyFromTemplateProps {
  application: Application;
  handleCopy(manifest: IManifest): void;
}

export interface ICopyFromTemplateState {
  show: boolean;
}

class CopyFromTemplateButton extends React.Component<ICopyFromTemplateProps, ICopyFromTemplateState> {
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
        <a className="clickable" onClick={this.toggle}>
          copy from running infrastructure
        </a>
      </>
    );
  }
}

export const KUBERNETES_COPY_FROM_TEMPLATE_BUTTON = 'spinnaker.kubernetes.copyFromTemplateButton.component';
module(KUBERNETES_COPY_FROM_TEMPLATE_BUTTON, []).component(
  'kubernetesCopyFromTemplateButton',
  react2angular(CopyFromTemplateButton, ['application', 'handleCopy']),
);
