import React from 'react';

import type { IStage } from '../domain';

export interface ILabelComponentProps {
  displayName?: string;
  stage: IStage;
}

export class LabelComponent extends React.Component<ILabelComponentProps> {
  public render() {
    const SubLabelComponent = this.props.stage.labelComponent;
    return (
      <div className="label-component">
        <SubLabelComponent displayName={this.props.displayName} stage={this.props.stage} />
      </div>
    );
  }
}
