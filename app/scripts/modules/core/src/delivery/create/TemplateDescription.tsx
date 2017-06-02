import * as React from 'react';
import { NgReact } from '@spinnaker/core';
import { IPipelineTemplate } from 'core/pipeline/config/templates/pipelineTemplate.service';

import './TemplateDescription.less';

export interface ITemplateDescriptionProps {
  template: IPipelineTemplate;
  loading: boolean;
  loadingError: boolean;
}

export class TemplateDescription extends React.Component<ITemplateDescriptionProps, void> {
  public render() {
    const { Spinner } = NgReact;
    return (
      <div className="col-md-12 template-description">
        {this.props.loading && (
          <div className="spinner">
            <Spinner radius={5} width={3} length={8} />
          </div>
        )}
        {this.props.template && (
          <div className="alert alert-info">
            <strong>{this.props.template.metadata.name}</strong>
            {this.props.template.metadata.owner && (<p className="small">{this.props.template.metadata.owner}</p>)}
            <p className="small">{this.props.template.metadata.description || 'No template description provided.'}</p>
          </div>
        )}
        {this.props.loadingError && (
          <div className="alert alert-danger">
            <p>There was an error loading the template.</p>
          </div>
        )}
      </div>
    );
  }
}
