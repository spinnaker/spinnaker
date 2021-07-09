import React from 'react';

import { IStage } from '../domain';

export interface ILabelComponentProps {
  stage: IStage;
}

export class LabelComponent extends React.Component<ILabelComponentProps> {
  public render() {
    const SubLabelComponent = this.props.stage.labelComponent;
    return (
      <div className="label-component">
        <SubLabelComponent stage={this.props.stage} />
      </div>
    );
  }
}
