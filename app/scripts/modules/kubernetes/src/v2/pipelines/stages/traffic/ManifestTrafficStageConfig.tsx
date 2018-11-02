import * as React from 'react';

import { IStage, IStageConfigProps } from '@spinnaker/core';
import { ManifestSelector } from 'kubernetes/v2/manifest/selector/ManifestSelector';
import { IManifestSelector, SelectorMode } from 'kubernetes/v2/manifest/selector/IManifestSelector';

export interface IKubernetesManifestStageConfigProps extends IStageConfigProps {
  stage: IManifestSelector & IStage;
}

export class ManifestTrafficStageConfig extends React.Component<IKubernetesManifestStageConfigProps> {
  private onChange = (stage: IManifestSelector) => {
    Object.assign(this.props.stage, stage);
    this.props.stageFieldUpdated();
  };

  public render() {
    const selector = { ...this.props.stage };
    return (
      <div className="form-horizontal">
        <h4>Manifest</h4>
        <div className="horizontal-rule" />
        <ManifestSelector
          application={this.props.application}
          selector={selector}
          modes={[SelectorMode.Static, SelectorMode.Dynamic]}
          onChange={this.onChange}
          includeSpinnakerKinds={['serverGroups']}
        />
      </div>
    );
  }
}
