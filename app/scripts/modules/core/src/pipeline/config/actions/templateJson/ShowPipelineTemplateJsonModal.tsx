import classNames from 'classnames';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { IPipelineTemplateV2 } from '../../../../domain';
import { ModalClose } from '../../../../modal';
import { IModalComponentProps, JsonEditor } from '../../../../presentation';
import { CopyToClipboard, JsonUtils, noop } from '../../../../utils';
import { Spinner } from '../../../../widgets/spinners/Spinner';

import './ShowPipelineTemplateJsonModal.less';

export interface IShowPipelineTemplateJsonModalProps extends IModalComponentProps {
  template: IPipelineTemplateV2;
  editable?: boolean;
  modalHeading?: string;
  descriptionText?: string;
  saveTemplate?: (template: IPipelineTemplateV2) => PromiseLike<boolean>;
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
        <>
          <ModalClose dismiss={dismissModal} />
          <Modal.Header>
            <Modal.Title>{modalHeading}</Modal.Title>
          </Modal.Header>

          <Modal.Body className="flex-fill">
            <div className="text-center">
              <Spinner size="medium" message="Saving ..." />
            </div>
          </Modal.Body>
        </>
      );
    }
    return (
      <>
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>{modalHeading}</Modal.Title>
        </Modal.Header>
        <Modal.Body className="flex-fill">
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
                  onChange={(e) => this.onChange(e, 'name')}
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
                  onChange={(e) => this.onChange(e, 'description')}
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
                  onChange={(e) => this.onChange(e, 'owner')}
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
        </Modal.Body>
        <Modal.Footer>
          {saveError && <span className="show-pipeline-template-json-modal__save-error">{String(saveError)}</span>}
          <ShowPipelineTemplateJsonModalButtons onSave={saveTemplate && this.saveTemplate} onClose={dismissModal} />
        </Modal.Footer>
      </>
    );
  }
}

interface IShowPipelineTemplateJsonModalButtons {
  onSave: (e: React.SyntheticEvent<HTMLButtonElement>) => void;
  onClose: (e: React.SyntheticEvent<HTMLButtonElement>) => void;
}

const ShowPipelineTemplateJsonModalButtons = (props: IShowPipelineTemplateJsonModalButtons) => {
  const { onClose, onSave } = props;
  const closeClasses = classNames({ btn: true, 'btn-primary': !onSave });
  const saveClasses = classNames({ btn: true, 'btn-primary': true });
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
