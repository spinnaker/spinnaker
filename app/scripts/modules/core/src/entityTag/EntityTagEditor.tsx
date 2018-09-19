import * as React from 'react';
import { Field, FieldProps, Form, Formik, FormikErrors } from 'formik';
import { Modal } from 'react-bootstrap';

import {
  UUIDGenerator,
  Application,
  EntityTagWriter,
  HelpField,
  IEntityRef,
  IEntityTag,
  TaskMonitor,
  SubmitButton,
  Markdown,
} from 'core';

import { ReactModal } from 'core/presentation';
import { NgReact } from 'core/reactShims/ngReact';
import { EntityRefBuilder } from './entityRef.builder';
import { noop } from 'core/utils';

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
  ownerIndex: number | string;
}

export class EntityTagEditor extends React.Component<IEntityTagEditorProps, IEntityTagEditorState> {
  public static defaultProps: Partial<IEntityTagEditorProps> = {
    onUpdate: noop,
  };

  /** Shows the Entity Tag Editor modal */
  public static show(props: IEntityTagEditorProps): Promise<void> {
    return ReactModal.show(EntityTagEditor, props);
  }

  constructor(props: IEntityTagEditorProps) {
    super(props);

    const { tag } = this.props;
    const ownerIndex = this.props.ownerOptions ? 0 : -1; // Assuming that the first option is the provided option
    tag.name = tag.name || `spinnaker_ui_${tag.value.type}:${UUIDGenerator.generateUuid()}`;

    this.state = {
      taskMonitor: null,
      initialValues: {
        message: (tag.value && tag.value.message) || '',
        ownerIndex,
      },
      isSubmitting: false,
    };
  }

  private validate = (values: IEntityTagEditorValues): Partial<FormikErrors<IEntityTagEditorValues>> => {
    const errors: Partial<FormikErrors<IEntityTagEditorValues>> = {};
    if (!values.message) {
      errors.message = 'Please enter a message';
    }
    return errors;
  };

  private close = (args?: any): void => {
    this.props.dismissModal.apply(null, args);
  };

  private upsertTag = (values: IEntityTagEditorValues): void => {
    const { application, isNew, tag, onUpdate, ownerOptions } = this.props;
    const ownerIndex = Number(values.ownerIndex);

    const ownerOption = ownerIndex !== -1 && (ownerOptions || [])[ownerIndex];
    const owner = ownerOption ? ownerOption.owner : this.props.owner;
    const entityType = ownerOption ? ownerOption.type : this.props.entityType;

    const entityRef: IEntityRef = this.props.entityRef || EntityRefBuilder.getBuilder(entityType)(owner);

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
    const { isNew, tag, ownerOptions } = this.props;
    const { initialValues, isSubmitting } = this.state;

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

        <Formik<{}, IEntityTagEditorValues>
          initialValues={initialValues}
          onSubmit={this.upsertTag}
          validate={this.validate}
          render={({ isValid, values }) => (
            <Form className="form-horizontal">
              <Modal.Header>
                <h3>
                  {isNew ? 'Create' : 'Update'} {tag.value.type}
                </h3>
                {closeButton}
              </Modal.Header>
              <Modal.Body className="entity-tag-editor-modal">
                <div className="row">
                  <div className="col-md-10 col-md-offset-1">
                    <div className="form-group">
                      <div className="col-md-3 sm-label-right">Message</div>
                      <div className="col-md-9">
                        <Field
                          name="message"
                          render={({ field }: FieldProps<IEntityTagEditorValues>) => (
                            <textarea className="form-control input-sm" {...field} rows={5} required={true} />
                          )}
                        />
                        <div className="small text-right">
                          {' '}
                          <div>
                            Markdown is okay <HelpField id="markdown.examples" />
                          </div>{' '}
                        </div>
                      </div>
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
                </div>

                {ownerOptions &&
                  ownerOptions.length && (
                    <div className="row">
                      <div className="col-md-10 col-md-offset-1">
                        <div className="form-group">
                          <div className="col-md-3 sm-label-right">
                            <b>Applies to</b>
                          </div>
                          <div className="col-md-9">
                            {ownerOptions.map((option, index) => (
                              <div key={option.label} className="radio">
                                <label>
                                  <Field
                                    name="ownerIndex"
                                    type="radio"
                                    value={index}
                                    checked={index === Number(values.ownerIndex)}
                                  />
                                  <span className="marked">
                                    <Markdown message={option.label} />
                                  </span>
                                </label>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    </div>
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
