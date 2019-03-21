import * as React from 'react';

import { IModalComponentProps, JsonEditor } from 'core/presentation';
import { IPipeline, IPipelineTemplateV2 } from 'core/domain';
import { CopyToClipboard, noop, JsonUtils } from 'core/utils';
import { PipelineTemplateV2Service } from 'core/pipeline';

import './ShowPipelineTemplateJsonModal.less';

export interface IShowPipelineTemplateJsonModalProps extends IModalComponentProps {
  ownerEmail: string;
  pipeline: IPipeline;
}

export interface IShowPipelineTemplateJsonModalState {
  template: IPipelineTemplateV2;
}

export class ShowPipelineTemplateJsonModal extends React.Component<
  IShowPipelineTemplateJsonModalProps,
  IShowPipelineTemplateJsonModalState
> {
  public static defaultProps: Partial<IShowPipelineTemplateJsonModalProps> = {
    dismissModal: noop,
  };

  constructor(props: IShowPipelineTemplateJsonModalProps) {
    super(props);

    const template = PipelineTemplateV2Service.createPipelineTemplate(props.pipeline, props.ownerEmail);
    this.state = { template };
  }

  private onChange = (e: React.ChangeEvent<HTMLInputElement>, property: string) =>
    this.setState({
      template: {
        ...this.state.template,
        metadata: {
          ...this.state.template.metadata,
          [property]: e.target.value,
        },
      },
    });

  public render() {
    const { dismissModal } = this.props;
    const { template } = this.state;
    const sortedTemplate = JsonUtils.sortObject(template);
    const templateStr = JsonUtils.makeStringFromObject(sortedTemplate, 0);
    const templateStrWithSpacing = JsonUtils.makeStringFromObject(sortedTemplate);

    return (
      <div className="flex-fill">
        <div className="modal-header">
          <h3>Export as Pipeline Template</h3>
        </div>
        <div className="modal-body flex-fill">
          <p>The JSON below is the templated version of your pipeline. Save it by copy/pasting to the Spin CLI tool.</p>
          <form className="form-horizontal">
            <div className="form-group">
              <label htmlFor="template-name" className="col-md-3 sm-label-right">
                Name
              </label>
              <div className="col-md-7">
                <input
                  id="template-name"
                  className="form-control input-sm"
                  type="text"
                  value={template.metadata.name}
                  onChange={e => this.onChange(e, 'name')}
                />
              </div>
            </div>
            <div className="form-group">
              <label htmlFor="template-description" className="col-md-3 sm-label-right">
                Description
              </label>
              <div className="col-md-7">
                <input
                  id="template-description"
                  className="form-control input-sm"
                  type="text"
                  value={template.metadata.description}
                  onChange={e => this.onChange(e, 'description')}
                  placeholder="Template Description"
                />
              </div>
            </div>
            <div className="form-group">
              <label htmlFor="template-owner" className="col-md-3 sm-label-right">
                Owner
              </label>
              <div className="col-md-7">
                <input
                  id="template-owner"
                  className="form-control input-sm"
                  type="text"
                  value={template.metadata.owner}
                  onChange={e => this.onChange(e, 'owner')}
                />
              </div>
            </div>
            <div className="show-pipeline-template-json-modal__copy text-right">
              <CopyToClipboard
                buttonInnerNode={<a>Copy the spin command for saving this template</a>}
                text={`echo '${templateStr}' | spin pipeline-templates save`}
              />
            </div>
          </form>
          <JsonEditor value={templateStrWithSpacing} readOnly={true} />
        </div>
        <div className="modal-footer">
          <button className="btn btn-primary" onClick={dismissModal}>
            Close
          </button>
        </div>
      </div>
    );
  }
}
