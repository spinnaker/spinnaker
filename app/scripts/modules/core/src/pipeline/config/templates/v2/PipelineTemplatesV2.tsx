import * as React from 'react';
import { DateTime } from 'luxon';
import { get } from 'lodash';
import { IPipelineTemplateV2 } from 'core/domain/IPipelineTemplateV2';
import { ReactModal } from 'core/presentation';
import {
  ShowPipelineTemplateJsonModal,
  IShowPipelineTemplateJsonModalProps,
} from 'core/pipeline/config/actions/templateJson/ShowPipelineTemplateJsonModal';
import { PipelineTemplateReader } from '../PipelineTemplateReader';

import './PipelineTemplatesV2.less';

export interface IPipelineTemplatesV2State {
  templates: IPipelineTemplateV2[];
  fetchError: string;
}

export const PipelineTemplatesV2Error = (props: { message: string }) => {
  return (
    <div className="pipeline-templates-error-banner horizontal middle center heading-4">
      <i className="fa fa-exclamation-triangle" />
      <span>{props.message}</span>
    </div>
  );
};

export class PipelineTemplatesV2 extends React.Component<{}, IPipelineTemplatesV2State> {
  constructor(props: {}) {
    super(props);
    this.state = { templates: [], fetchError: null };
  }

  public componentDidMount() {
    this.fetchTemplates();
  }

  private fetchTemplates = () => {
    const templatesPromise = PipelineTemplateReader.getV2PipelineTemplateList();
    templatesPromise.then(
      templates => {
        this.setState({ templates });
      },
      err => {
        const message: string = get(err, 'data.message') || get(err, 'message') || 'Unknown error.';
        this.setState({ fetchError: message });
      },
    );
  };

  private idForTemplate = (template: IPipelineTemplateV2) => {
    const { id, version = '', digest = '' } = template;
    return `${id}:${version}:${digest}`;
  };

  private getUpdateTimeForTemplate = (template: IPipelineTemplateV2) => {
    const millis = Number.parseInt(template.updateTs, 10);
    if (isNaN(millis)) {
      return '';
    }
    const dt = DateTime.fromMillis(millis);
    return dt.toLocaleString(DateTime.DATETIME_SHORT);
  };

  private showTemplateJson = (template: IPipelineTemplateV2) => {
    const props = {
      template,
      editable: false,
      modalHeading: 'View Pipeline Template',
      descriptionText: 'The JSON below contains the metadata, variables and pipeline definition for this template.',
    };
    ReactModal.show<IShowPipelineTemplateJsonModalProps>(ShowPipelineTemplateJsonModal, props, {
      dialogClassName: 'modal-lg modal-fullscreen',
    });
  };

  public render() {
    return (
      <>
        <div className="infrastructure">
          <div className="infrastructure-section search-header">
            <div className="container">
              <h2 className="header-section">
                <span className="search-label">Pipeline Templates</span>
              </h2>
            </div>
          </div>
          <div className="infrastructure-section">
            <div className="container">
              {this.state.fetchError && (
                <PipelineTemplatesV2Error
                  message={`There was an error fetching pipeline templates: ${this.state.fetchError}`}
                />
              )}
              <table className="table">
                <thead>
                  <tr>
                    <th>Name</th>
                    <th>Owner</th>
                    <th>Updated</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {(this.state.templates || []).map(template => {
                    const { metadata } = template;
                    return (
                      <tr key={this.idForTemplate(template)}>
                        <td>{metadata.name || '-'}</td>
                        <td>{metadata.owner || '-'}</td>
                        <td>{this.getUpdateTimeForTemplate(template) || '-'}</td>
                        <td className="pipeline-template-actions">
                          <button className="link" onClick={() => this.showTemplateJson(template)}>
                            View
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </>
    );
  }
}
