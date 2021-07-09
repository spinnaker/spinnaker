import classNames from 'classnames';
import React from 'react';

import { SETTINGS } from '../../config/settings';
import { IPipelineTemplate } from '../config/templates/PipelineTemplateReader';
import { IPipelineTemplateV2 } from '../../domain/IPipelineTemplateV2';
import { Spinner } from '../../widgets/spinners/Spinner';

import './TemplateDescription.less';

export interface ITemplateDescriptionProps {
  template: IPipelineTemplate | IPipelineTemplateV2;
  loading: boolean;
  loadingError: boolean;
}

export class TemplateDescription extends React.Component<ITemplateDescriptionProps> {
  private isV1Template(template: IPipelineTemplate | IPipelineTemplateV2): template is IPipelineTemplate {
    return template.schema === '1';
  }

  public render() {
    const { loading, loadingError, template } = this.props;

    return (
      <div className={classNames('col-md-12', 'template-description', { 'template-description--loading': loading })}>
        {loading && (
          <div className="spinner">
            <Spinner size="small" />
          </div>
        )}
        {template && (
          <div className="alert alert-info">
            <strong>{template.metadata.name}</strong>
            {this.isV1Template(template) && template.selfLink && (
              <p className="small">
                <a href={this.buildTemplateResolutionLink(template.selfLink)} target="_blank">
                  {template.selfLink}
                </a>
              </p>
            )}
            {template.metadata.owner && <p className="small">{template.metadata.owner}</p>}
            <p className="small">{template.metadata.description || 'No template description provided.'}</p>
          </div>
        )}
        {loadingError && (
          <div className="alert alert-danger">
            <p>There was an error loading the template.</p>
          </div>
        )}
      </div>
    );
  }

  private buildTemplateResolutionLink(templateLink: string): string {
    return `${SETTINGS.gateUrl}/pipelineTemplates/resolve?source=${templateLink}`;
  }
}
