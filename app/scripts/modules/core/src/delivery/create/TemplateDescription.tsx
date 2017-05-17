import * as React from 'react';
import {ITemplateMetadata} from 'core/pipeline/config/templates/pipelineTemplate.service';

import './TemplateDescription.less';

interface IProps {
  templateMetadata: ITemplateMetadata
}

interface IState { }

export class TemplateDescription extends React.Component<IProps, IState> {
  public render() {
    if (!this.props.templateMetadata) {
      return null;
    }

    return (
      <div className="col-md-12 template-description">
        <div className="alert alert-info">
          <strong>{this.props.templateMetadata.name}</strong>
          {this.props.templateMetadata.owner && (<p className="small">{this.props.templateMetadata.owner}</p>)}
          <p className="small">{this.props.templateMetadata.description || 'No description provided.'}</p>
        </div>
      </div>
    );
  }
}
