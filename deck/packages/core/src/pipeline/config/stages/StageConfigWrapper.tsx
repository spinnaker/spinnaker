import React from 'react';

import type { IStageConfigProps } from './common/IStageConfigProps';

export interface IStageConfigWrapperProps extends IStageConfigProps {
  component: React.ComponentType<any>;
}

/* This wrapper refreshes stage config components after updateStageField mutates the
 * shared stage object.
 */

export class StageConfigWrapper extends React.Component<IStageConfigWrapperProps> {
  public render() {
    const { component: StageConfig, updateStageField, ...otherProps } = this.props;
    return (
      <StageConfig
        updateStage={updateStageField}
        updateStageField={(changes: { [key: string]: any }) => {
          updateStageField(changes);
          this.forceUpdate();
        }}
        {...otherProps}
      />
    );
  }
}
