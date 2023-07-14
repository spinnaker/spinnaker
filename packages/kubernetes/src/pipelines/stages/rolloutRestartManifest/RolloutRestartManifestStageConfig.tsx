import { defaults } from 'lodash';
import React from 'react';

import type { Application, IStage, IStageConfigProps } from '@spinnaker/core';

import type { IManifestSelector } from '../../../manifest/selector/IManifestSelector';
import { SelectorMode } from '../../../manifest/selector/IManifestSelector';
import { ManifestSelector } from '../../../manifest/selector/ManifestSelector';

export interface IKubernetesManifestStageConfigProps extends IStageConfigProps {
  application: Application;
  stage: IManifestSelector & IStage;
  stageFieldUpdated: () => void;
}

export class RolloutRestartManifestStageConfig extends React.Component<IKubernetesManifestStageConfigProps> {
  public componentDidMount = (): void => {
    defaults(this.props.stage, {
      app: this.props.application.name,
      cloudProvider: 'kubernetes',
    });
    this.props.stageFieldUpdated();
  };

  private onChange = (stage: IManifestSelector): void => {
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
          modes={[SelectorMode.Static, SelectorMode.Dynamic, SelectorMode.Label]}
          onChange={this.onChange}
          includeSpinnakerKinds={null}
        />
      </div>
    );
  }
}
