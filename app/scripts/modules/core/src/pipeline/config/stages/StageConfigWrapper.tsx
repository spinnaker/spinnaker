import React from 'react';

import { IStageConfigProps } from './common/IStageConfigProps';

export interface IStageConfigWrapperProps extends IStageConfigProps {
  component: React.ComponentType<any>;
}

/* This wrapper component exists so that StageConfig components don't have to manually
 * call this.forceUpdate() after updating a stage field. The only reason it is currently
 * needed is because the stage config object is managed in angular, above the render root.
 * When it is eventually migrated to react, it will simply be part of the state of some
 * parent component and this wrapper can be removed.
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
