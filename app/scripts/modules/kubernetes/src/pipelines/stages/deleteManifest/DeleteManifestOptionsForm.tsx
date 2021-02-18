import React from 'react';

import { noop, StageConfigField } from '@spinnaker/core';

import { IDeleteOptions } from '../../../manifest/delete/delete.controller';

import './deleteManifestOptionsForm.less';

export interface IDeleteManifestOptionsFormProps {
  onOptionsChange: (options: IDeleteOptions) => void;
  options: IDeleteOptions;
}

export interface IDeleteManifestOptionsFormState {
  options: IDeleteOptions;
}

export default class DeleteManifestOptionsForm extends React.Component<
  IDeleteManifestOptionsFormProps,
  IDeleteManifestOptionsFormState
> {
  public static defaultProps: Partial<IDeleteManifestOptionsFormProps> = {
    onOptionsChange: noop,
    options: {
      cascading: true,
      gracePeriodSeconds: null,
    },
  };

  public constructor(props: IDeleteManifestOptionsFormProps) {
    super(props);
    this.state = {
      options: props.options,
    };
  }

  private setStateAndUpdateStage = (options: IDeleteOptions) => {
    this.props.onOptionsChange(options);
    this.setState({ options });
  };

  private onCascadingChange = (e: any): void => {
    this.setStateAndUpdateStage({
      ...this.state.options,
      cascading: e.target.checked,
    });
  };

  private onGracePeriodChange = (e: any): void => {
    const integerValue = parseInt(e.target.value, 10);
    const gracePeriodSeconds = !isNaN(integerValue) ? integerValue : null;
    this.setStateAndUpdateStage({
      ...this.state.options,
      gracePeriodSeconds,
    });
  };

  public render() {
    const {
      options: { cascading, gracePeriodSeconds },
    } = this.state;
    return (
      <>
        <StageConfigField helpKey="kubernetes.manifest.delete.cascading" label="Cascading">
          <div className="checkbox">
            <input
              checked={cascading}
              className="delete-manifest-options-input-cascading"
              onChange={this.onCascadingChange}
              type="checkbox"
            />
          </div>
        </StageConfigField>
        <StageConfigField helpKey="kubernetes.manifest.delete.gracePeriod" label="Grace Period">
          <input
            className="form-control input-sm delete-manifest-options-input-grace-period"
            min="0"
            onChange={this.onGracePeriodChange}
            type="number"
            value={Number.isInteger(gracePeriodSeconds) ? gracePeriodSeconds : ''}
          />
          <span className="form-control-static">{gracePeriodSeconds === 1 ? 'second' : 'seconds'}</span>
        </StageConfigField>
      </>
    );
  }
}
