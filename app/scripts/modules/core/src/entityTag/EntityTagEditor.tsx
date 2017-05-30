import { IDeferred } from 'angular';
import { $q } from 'ngimport';
import * as Formsy from 'formsy-react';
import * as marked from 'marked';
import * as React from 'react';
import { Modal } from 'react-bootstrap';
import { IModalServiceInstance } from 'angular-ui-bootstrap';
import autoBindMethods from 'class-autobind-decorator';
import * as DOMPurify from 'dompurify';

import {
  UUIDGenerator, Application, EntityTagWriter, HelpField, IEntityRef, IEntityTag,
  ReactInjector, TaskMonitor, TaskMonitorBuilder, SubmitButton
} from 'core';

import { BasicFieldLayout, TextArea, ReactModal } from 'core/presentation';
import { NgReact } from 'core/reactShims/ngReact';
import { EntityRefBuilder } from './entityRef.builder';
import { noop } from 'core/utils';

import './EntityTagEditor.less';
import { Form } from 'formsy-react';

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
  onHide?(event: any): void;
  onUpdate?(): void;
}

export interface IEntityTagEditorState {
  taskMonitor: TaskMonitor;
  message: string;
  show: boolean;
  isValid: boolean;
  isSubmitting: boolean;
  owner: IOwner;
  entityType: string;
}

@autoBindMethods
export class EntityTagEditor extends React.Component<IEntityTagEditorProps, IEntityTagEditorState> {
  public static defaultProps: Partial<IEntityTagEditorProps> = {
    onHide: noop,
    onUpdate: noop,
  };

  private taskMonitorBuilder: TaskMonitorBuilder = ReactInjector.taskMonitorBuilder;
  private entityTagWriter: EntityTagWriter = ReactInjector.entityTagWriter;
  private $uibModalInstanceEmulation: IModalServiceInstance & { deferred?: IDeferred<any> };
  private form: Form;

  /** Shows the Entity Tag Editor modal */
  public static show(props: IEntityTagEditorProps): Promise<void> {
    return ReactModal.show(React.createElement(EntityTagEditor, props));
  }

  constructor(props: IEntityTagEditorProps) {
    super(props);

    const { ownerOptions, tag, entityType } = this.props;
    const owner = this.props.owner || (ownerOptions && ownerOptions.length && ownerOptions[0].owner);
    tag.name = tag.name || `spinnaker_ui_${tag.value.type}:${UUIDGenerator.generateUuid()}`;

    this.state = {
      taskMonitor: null,
      message: tag.value && tag.value.message,
      show: true,
      isValid: false,
      isSubmitting: false,
      owner: owner,
      entityType: entityType,
    };

    const deferred = $q.defer();
    const promise = deferred.promise;
    this.$uibModalInstanceEmulation = {
      result: promise,
      close: () => this.setState({ show: false }),
      dismiss: () => this.setState({ show: false }),
    } as IModalServiceInstance;
    Object.assign(this.$uibModalInstanceEmulation, { deferred });
  }

  private handleMessageChanged(message: string): void {
    this.setState({ message });
  }

  private handleOwnerOptionChanged(option: IOwnerOption): void {
    this.setState({ owner: option.owner, entityType: option.type });
  }

  private onValid(): void {
    this.setState({ isValid: true });
  }

  private onInvalid(): void {
    this.setState({ isValid: false });
  }

  private onHide(): void {
    this.setState({ show: false });
    this.props.onHide.apply(null, arguments);
    this.$uibModalInstanceEmulation.deferred.resolve();
  }

  private upsertTag(data: { message: string; }): void {
    const { application, isNew, tag, onUpdate } = this.props;
    const { owner, entityType } = this.state;
    const entityRef: IEntityRef = this.props.entityRef || EntityRefBuilder.getBuilder(entityType)(owner);

    tag.value.message = data.message;

    const taskMonitor = this.taskMonitorBuilder.buildTaskMonitor({
      application: application,
      title: `${isNew ? 'Create' : 'Update'} ${this.props.tag.value.type} for ${entityRef.entityId}`,
      modalInstance: this.$uibModalInstanceEmulation,
      onTaskComplete: () => onUpdate(),
    });

    const submitMethod = () => {
      const promise = this.entityTagWriter.upsertEntityTag(application, tag, entityRef, isNew);
      const done = () => this.setState({ isSubmitting: false });
      promise.then(done, done);
      return promise;
    };

    taskMonitor.submit(submitMethod);

    this.setState({ taskMonitor, isSubmitting: true });
  }

  private refCallback(form: Form): void {
    this.form = form;
  }

  private submit(): void {
    this.form.submit();
  }

  public render() {
    const { isNew, tag, ownerOptions } = this.props;
    const { isValid, isSubmitting } = this.state;
    const message = this.state.message || '';

    const closeButton = (
      <div className="modal-close close-button pull-right">
        <a className="btn btn-link" onClick={this.onHide}>
          <span className="glyphicon glyphicon-remove" />
        </a>
      </div>
    );

    const submitLabel = `${isNew ? ' Create' : ' Update'} ${tag.value.type}`;
    const cancelButton = <button type="button" className="btn btn-default" onClick={this.onHide}>Cancel</button>;

    const { TaskMonitorWrapper } = NgReact;

    return (
      <Modal show={this.state.show} onHide={this.onHide} dialogClassName="entity-tag-editor-modal">

        <TaskMonitorWrapper monitor={this.state.taskMonitor} />

        <Formsy.Form
          ref={this.refCallback}
          role="form"
          name="form"
          className="form-horizontal"
          onSubmit={this.upsertTag}
          onValid={this.onValid}
          onInvalid={this.onInvalid}
        >
          <Modal.Header>
            <h3>{isNew ? 'Create' : 'Update'} {tag.value.type}</h3>
            {closeButton}
          </Modal.Header>

          <Modal.Body>
            <EntityTagMessage
              message={message}
              onMessageChanged={this.handleMessageChanged}
            />

            { ownerOptions && ownerOptions.length && (
              <OwnerOptions
                selectedOwner={this.state.owner}
                ownerOptions={ownerOptions}
                onOwnerOptionChanged={this.handleOwnerOptionChanged}
              />
            ) }
          </Modal.Body>

          <Modal.Footer>
            {cancelButton}

            <SubmitButton
              onClick={this.submit}
              label={submitLabel}
              isDisabled={!isValid || isSubmitting}
              submitting={this.state.isSubmitting}
            />

          </Modal.Footer>
        </Formsy.Form>

      </Modal>
    );
  }
}



interface IEntityTagMessageProps {
  message: string;
  onMessageChanged(message: string): void;
}

@autoBindMethods
class EntityTagMessage extends React.Component<IEntityTagMessageProps, {}> {
  private handleTextareaChanged(event: React.FormEvent<HTMLTextAreaElement>): void {
    this.props.onMessageChanged(event.currentTarget.value);
  }

  public render() {
    const { message } = this.props;

    return (
      <div className="row">
        <div className="col-md-10 col-md-offset-1">

          <TextArea
            label="Message"
            Help={<div>Markdown is okay <HelpField id="markdown.examples"/></div>}
            Layout={BasicFieldLayout}
            name="message"
            required={true}
            validationErrors={{ isDefaultRequiredValue: 'Please enter a message' }}
            onChange={this.handleTextareaChanged}
            value={message}
            rows={5}
            className="form-control input-sm"
          />

          { message && (
            <div className="form-group preview">
              <div className="col-md-3 sm-label-right">
                <strong>Preview</strong>
              </div>
              <div className="col-md-9">
                <div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(marked(message)) }}/>
              </div>
            </div>
          ) }
        </div>
      </div>
    )
  }
}



interface IOwnerOptionsProps {
  selectedOwner: any;
  ownerOptions: IOwnerOption[];
  onOwnerOptionChanged(owner: IOwnerOption): void;
}

@autoBindMethods
class OwnerOptions extends React.Component<IOwnerOptionsProps, void> {
  public handleOwnerOptionChanged(option: IOwnerOption): void {
    this.props.onOwnerOptionChanged(option);
  }

  public render() {
    const { ownerOptions, selectedOwner } = this.props;

    return (
      <div className="row">
        <div className="col-md-10 col-md-offset-1">
          <div className="form-group">
            <div className="col-md-3 sm-label-right">
              <b>Applies to</b>
            </div>
            <div className="col-md-9">
              { ownerOptions.map((option: IOwnerOption) => (
                <div key={option.label} className="radio">
                  <label>
                    <OwnerOption
                      option={option}
                      selectedOwner={selectedOwner}
                      onOwnerOptionChanged={this.handleOwnerOptionChanged}
                    />
                    <span className="marked" dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(marked(option.label)) }}/>
                  </label>
                </div>
              )) }
            </div>
          </div>
        </div>
      </div>
    );
  }
}

interface IOwnerOptionProps {
  option?: IOwnerOption;
  selectedOwner?: any;
  onOwnerOptionChanged?(option: IOwnerOption): void;
}

@autoBindMethods
class OwnerOption extends React.Component<IOwnerOptionProps, any> {
  public handleOwnerChanged(): void {
    this.props.onOwnerOptionChanged(this.props.option);
  }

  public render() {
    const { option, selectedOwner } = this.props;
    return (
      <input
        name="owner"
        type="radio"
        value={option.label}
        onChange={this.handleOwnerChanged}
        checked={option.owner === selectedOwner}
      />
    );
  }
}
