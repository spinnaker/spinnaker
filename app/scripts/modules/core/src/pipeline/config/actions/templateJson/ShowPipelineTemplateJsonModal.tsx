import * as React from 'react';
import { IPromise } from 'angular';
import * as classnames from 'classnames';

import { Spinner } from 'core/widgets/spinners/Spinner';
import { IModalComponentProps, JsonEditor } from 'core/presentation';
import { IPipelineTemplateV2 } from 'core/domain';
import { CopyToClipboard, noop, JsonUtils } from 'core/utils';

import './ShowPipelineTemplateJsonModal.less';

export interface IShowPipelineTemplateJsonModalProps extends IModalComponentProps {
  template: IPipelineTemplateV2;
  editable?: boolean;
  modalHeading?: string;
  descriptionText?: string;
  saveTemplate?: (template: IPipelineTemplateV2) => IPromise<boolean>;
}

export interface IShowPipelineTemplateJsonModalState {
  template: IPipelineTemplateV2;
  saveError: Error;
  saving: boolean;
}

export class ShowPipelineTemplateJsonModal extends React.Component<
  IShowPipelineTemplateJsonModalProps,
  IShowPipelineTemplateJsonModalState
> {
  public static defaultProps: Partial<IShowPipelineTemplateJsonModalProps> = {
    dismissModal: noop,
    editable: true,
    modalHeading: 'Export as Pipeline Template',
    descriptionText:
      'The JSON below is the templated version of your pipeline. Save it by copy/pasting to the Spin CLI tool.',
  };

  constructor(props: IShowPipelineTemplateJsonModalProps) {
    super(props);
    this.state = { template: props.template, saveError: null, saving: false };
  }

  private onChange = (e: React.ChangeEvent<HTMLInputElement>, property: string) => {
    if (!this.props.editable) {
      return;
    }
    this.setState({
      template: {
        ...this.state.template,
        metadata: {
          ...this.state.template.metadata,
          [property]: e.target.value,
        },
      },
    });
  };

  private onTemplateSaved = (error?: Error) => {
    this.setState({ saveError: error, saving: false });
  };

  private saveTemplate = () => {
    this.setState({ saveError: null, saving: true });
    this.props.saveTemplate(this.state.template).then(
      (shouldClose: boolean) => {
        if (shouldClose) {
          this.props.dismissModal();
        } else {
          this.onTemplateSaved();
        }
      },
      (err: Error) => this.onTemplateSaved(err),
    );
  };

  public render() {
    const { dismissModal, editable, modalHeading, descriptionText, saveTemplate } = this.props;
    const { template, saveError, saving } = this.state;
    const sortedTemplate = JsonUtils.sortObject(template);
    const templateStr = JsonUtils.makeStringFromObject(sortedTemplate, 0);
    const templateStrWithSpacing = JsonUtils.makeStringFromObject(sortedTemplate);
    const disabled = !editable || !!saving;

    if (saving) {
      return (
        <div className="flex-fill">
          <div className="modal-header">
            <h3>{modalHeading}</h3>
          </div>
          <div className="modal-body flex-fill show-pipeline-template-json-modal__saving-spinner">
            <Spinner size="medium" message="Saving ..." />
          </div>
        </div>
      );
    }
    return (
      <div className="flex-fill">
        <div className="modal-header">
          <h3>{modalHeading}</h3>
        </div>
        <div className="modal-body flex-fill">
          <p>{descriptionText}</p>
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
                  disabled={disabled}
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
                  disabled={disabled}
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
                  disabled={disabled}
                />
              </div>
            </div>
            {!disabled && (
              <div className="show-pipeline-template-json-modal__copy text-right">
                <CopyToClipboard
                  buttonInnerNode={<a>Copy the spin command for saving this template</a>}
                  text={`echo '${templateStr}' | spin pipeline-templates save`}
                />
              </div>
            )}
          </form>
          <JsonEditor value={templateStrWithSpacing} readOnly={true} />
        </div>
        <div className="modal-footer">
          {saveError && <span className="show-pipeline-template-json-modal__save-error">{String(saveError)}</span>}
          <ShowPipelineTemplateJsonModalButtons onSave={saveTemplate && this.saveTemplate} onClose={dismissModal} />
        </div>
      </div>
    );
  }
}

interface IShowPipelineTemplateJsonModalButtons {
  onSave: (e: React.SyntheticEvent<HTMLButtonElement>) => void;
  onClose: (e: React.SyntheticEvent<HTMLButtonElement>) => void;
}

const ShowPipelineTemplateJsonModalButtons = (props: IShowPipelineTemplateJsonModalButtons) => {
  const { onClose, onSave } = props;
  const closeClasses = classnames({ btn: true, 'btn-primary': !onSave });
  const saveClasses = classnames({ btn: true, 'btn-primary': true });
  return (
    <>
      <button className={closeClasses} onClick={onClose}>
        Close
      </button>
      {onSave && (
        <button className={saveClasses} onClick={onSave}>
          Save
        </button>
      )}
    </>
  );
};
