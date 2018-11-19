import * as React from 'react';
import { Form, Formik } from 'formik';
import { Modal } from 'react-bootstrap';

import { Application } from 'core/application';
import { IEntityRef, IEntityTag } from 'core/domain';
import { HelpField } from 'core/help';
import { SubmitButton } from 'core/modal';
import { FormField, FormikFormField, Markdown, RadioButtonInput, ReactModal, TextAreaInput } from 'core/presentation';
import { NgReact } from 'core/reactShims';
import { TaskMonitor } from 'core/task';
import { noop, UUIDGenerator } from 'core/utils';

import { EntityRefBuilder } from './entityRef.builder';
import { EntityTagWriter } from './entityTags.write.service';

import './EntityTagEditor.less';

export interface IOwner {
  name: string;
  cloudProvider: string;
  region: string;
  account: string;
}

export interface IOwnerOption {
  label: string;
  type: string;
  owner: IOwner;
  isDefault: boolean;
}

export interface IEntityTagEditorProps {
  owner: IOwner;
  application: Application;
  entityType: string;
  tag: IEntityTag;
  ownerOptions: IOwnerOption[];
  entityRef: IEntityRef;
  isNew: boolean;
  show?: boolean;
  closeModal?(result?: any): void; // provided by ReactModal
  dismissModal?(rejection?: any): void; // provided by ReactModal
  onUpdate?(): void;
}

export interface IEntityTagEditorState {
  taskMonitor: TaskMonitor;
  isSubmitting: boolean;
  initialValues: IEntityTagEditorValues;
}

export interface IEntityTagEditorValues {
  message: string;
  ownerOption?: IOwnerOption;
}

export class EntityTagEditor extends React.Component<IEntityTagEditorProps, IEntityTagEditorState> {
  public static defaultProps: Partial<IEntityTagEditorProps> = {
    onUpdate: noop,
    ownerOptions: [],
  };

  /** Shows the Entity Tag Editor modal */
  public static show(props: IEntityTagEditorProps): Promise<void> {
    return ReactModal.show(EntityTagEditor, props);
  }

  constructor(props: IEntityTagEditorProps) {
    super(props);

    const { tag } = this.props;
    const ownerOptions = this.props.ownerOptions || [];
    const defaultOwnerOption = ownerOptions.find(opt => opt.isDefault) || ownerOptions[0];
    tag.name = tag.name || `spinnaker_ui_${tag.value.type}:${UUIDGenerator.generateUuid()}`;

    this.state = {
      taskMonitor: null,
      initialValues: {
        message: (tag.value && tag.value.message) || '',
        ownerOption: defaultOwnerOption,
      },
      isSubmitting: false,
    };
  }

  private close = (args?: any): void => {
    this.props.dismissModal.apply(null, args);
  };

  private upsertTag = (values: IEntityTagEditorValues): void => {
    const { application, isNew, tag, onUpdate } = this.props;

    const owner: IOwner = values.ownerOption ? values.ownerOption.owner : this.props.owner;
    const entityType: string = values.ownerOption ? values.ownerOption.type : this.props.entityType;

    const entityRef: IEntityRef = this.props.entityRef || EntityRefBuilder.getBuilder(entityType)(owner);
    if (!entityRef.application) {
      entityRef.application = application.name;
    }

    tag.value.message = values.message;

    const onClose = (result: any) => this.props.closeModal(result);
    const onDismiss = (result: any) => this.props.dismissModal(result);
    const modalInstance = TaskMonitor.modalInstanceEmulation(onClose, onDismiss);
    const taskMonitor = new TaskMonitor({
      application,
      modalInstance,
      title: `${isNew ? 'Create' : 'Update'} ${this.props.tag.value.type} for ${entityRef.entityId}`,
      onTaskComplete: () => application.entityTags.refresh().then(() => onUpdate()),
    });

    const submitMethod = () => {
      const promise = EntityTagWriter.upsertEntityTag(application, tag, entityRef, isNew);
      const done = () => this.setState({ isSubmitting: false });
      promise.then(done, done);
      return promise;
    };

    taskMonitor.submit(submitMethod);

    this.setState({ taskMonitor, isSubmitting: true });
  };

  public render() {
    const { isNew, tag, ownerOptions: opts } = this.props;
    const { initialValues, isSubmitting } = this.state;
    const ownerOptions = opts || [];

    const closeButton = (
      <div className="modal-close close-button pull-right">
        <a className="btn btn-link" onClick={this.close}>
          <span className="glyphicon glyphicon-remove" />
        </a>
      </div>
    );

    const submitLabel = `${isNew ? ' Create' : ' Update'} ${tag.value.type}`;

    const { TaskMonitorWrapper } = NgReact;

    return (
      <div>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />

        <Formik<IEntityTagEditorValues>
          initialValues={initialValues}
          onSubmit={this.upsertTag}
          render={({ isValid, values, setFieldValue }) => (
            <Form className="form-horizontal">
              <Modal.Header>
                <h3>
                  {isNew ? 'Create' : 'Update'} {tag.value.type}
                </h3>
                {closeButton}
              </Modal.Header>

              <Modal.Body className="entity-tag-editor-modal">
                <div className="">
                  <FormikFormField
                    label="Message"
                    name="message"
                    required={true}
                    input={props => <TextAreaInput {...props} rows={5} required={true} />}
                  />
                  <div className="small text-right">
                    Markdown is okay <HelpField id="markdown.examples" />
                  </div>

                  {values.message && (
                    <div className="form-group preview">
                      <div className="col-md-3 sm-label-right">
                        <strong>Preview</strong>
                      </div>
                      <div className="col-md-9">
                        <Markdown message={values.message} />
                      </div>
                    </div>
                  )}
                </div>

                {!!ownerOptions.length && (
                  <FormField
                    name="ownerOption"
                    label="Applies To"
                    required={true}
                    value={values.ownerOption.label}
                    onChange={evt => {
                      const option = ownerOptions.find(opt => opt.label === evt.target.value);
                      setFieldValue('ownerOption', option);
                    }}
                    input={props => <RadioButtonInput {...props} options={ownerOptions.map(opt => opt.label)} />}
                  />
                )}
              </Modal.Body>

              <Modal.Footer>
                <button className="btn btn-default" disabled={isSubmitting} onClick={this.close} type="button">
                  Cancel
                </button>
                <SubmitButton
                  isDisabled={!isValid || isSubmitting}
                  submitting={isSubmitting}
                  isFormSubmit={true}
                  label={submitLabel}
                />
              </Modal.Footer>
            </Form>
          )}
        />
      </div>
    );
  }
}
