import { cloneDeep } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { PreconditionSelector } from './PreconditionSelector';
import type { Application } from '../../../application';
import type { IStage } from '../../../domain';
import { ModalClose } from '../../../modal';
import type { IPrecondition } from './preconditionTypes';
import type { IModalComponentProps } from '../../../presentation';
import { ReactModal } from '../../../presentation';

export interface IEditPreconditionModalProps extends IModalComponentProps<IPrecondition> {
  application: Application;
  precondition?: IPrecondition;
  strategy: boolean;
  upstreamStages: IStage[];
}

export class EditPreconditionModal extends React.Component<IEditPreconditionModalProps, IPrecondition> {
  public static show(props: Omit<IEditPreconditionModalProps, 'closeModal' | 'dismissModal'>): Promise<IPrecondition> {
    return ReactModal.show(EditPreconditionModal, props as IEditPreconditionModalProps, {
      dialogClassName: 'modal-md',
    });
  }

  public state: IPrecondition = cloneDeep(this.props.precondition) || { failPipeline: true };

  private updatePrecondition = (precondition: IPrecondition) => this.setState(precondition);

  private hasValue(value: any): boolean {
    return value !== undefined && value !== null && value !== '';
  }

  private isValidPrecondition(precondition: IPrecondition): boolean {
    const context = precondition.context || {};

    if (!precondition.type) {
      return false;
    }

    if (precondition.type === 'expression') {
      return this.hasValue(context.expression);
    }

    if (precondition.type === 'clusterSize') {
      const expectedSize = Number(context.expected);
      return (
        (this.props.strategy || this.hasValue(context.credentials)) &&
        Number.isFinite(expectedSize) &&
        expectedSize >= 0
      );
    }

    if (precondition.type === 'stageStatus') {
      return this.hasValue(context.stageName) && this.hasValue(context.stageStatus);
    }

    return true;
  }

  private submit = () => {
    if (this.isValidPrecondition(this.state)) {
      this.props.closeModal(this.state);
    }
  };

  public render() {
    const { dismissModal } = this.props;
    const validPrecondition = this.isValidPrecondition(this.state);

    return (
      <>
        <ModalClose dismiss={dismissModal || (() => {})} />
        <Modal.Header>
          <Modal.Title>Edit Precondition</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <PreconditionSelector
            application={this.props.application}
            precondition={this.state}
            strategy={this.props.strategy}
            upstreamStages={this.props.upstreamStages}
            onChange={this.updatePrecondition}
          />
        </Modal.Body>
        <Modal.Footer>
          <button type="button" className="btn btn-default" onClick={dismissModal || (() => {})}>
            Cancel
          </button>
          <button
            type="button"
            className="btn btn-primary"
            data-purpose="submit"
            disabled={!validPrecondition}
            onClick={this.submit}
          >
            <span className="far fa-check-circle" /> Update
          </button>
        </Modal.Footer>
      </>
    );
  }
}
