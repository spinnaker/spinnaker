import * as React from 'react';

import {IStage} from 'core/domain/IStage';

interface ILabelTemplateProps {
  stage: IStage;
}

export class LabelTemplate extends React.Component<ILabelTemplateProps, void> {
  public render() {
    const LabelTemplate = this.props.stage.labelTemplate;
    return (<div className="label-template"><LabelTemplate stage={this.props.stage}/></div>);
  }
}
