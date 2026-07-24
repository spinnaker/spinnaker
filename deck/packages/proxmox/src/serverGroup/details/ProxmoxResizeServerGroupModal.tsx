import React from 'react';
import { Modal, ModalFooter } from 'react-bootstrap';

import type { Application, IModalComponentProps, IServerGroup } from '@spinnaker/core';
import { ModalClose, noop, ReactInjector, ReactModal, TaskMonitor, TaskMonitorWrapper } from '@spinnaker/core';

export interface IProxmoxResizeServerGroupModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: IServerGroup;
}

export interface IProxmoxResizeServerGroupModalState {
  desired: number;
  taskMonitor: TaskMonitor;
}

/**
 * Scales the server group to the desired instance count. Scale-up clones new VMs from the
 * template the existing members were deployed from; scale-down removes the newest members.
 */
export class ProxmoxResizeServerGroupModal extends React.Component<
  IProxmoxResizeServerGroupModalProps,
  IProxmoxResizeServerGroupModalState
> {
  public static defaultProps: Partial<IProxmoxResizeServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: IProxmoxResizeServerGroupModalProps): Promise<void> {
    return ReactModal.show(ProxmoxResizeServerGroupModal, props);
  }

  constructor(props: IProxmoxResizeServerGroupModalProps) {
    super(props);
    this.state = {
      desired: Number(props.serverGroup.capacity?.desired ?? props.serverGroup.instances?.length ?? 1),
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Resizing your server group',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: () => this.props.application.serverGroups.refresh(),
      }),
    };
  }

  private submit = (): void => {
    const { serverGroup, application } = this.props;
    const { desired } = this.state;
    this.state.taskMonitor.submit(() =>
      ReactInjector.serverGroupWriter.resizeServerGroup(serverGroup, application, {
        capacity: { min: desired, max: desired, desired },
      }),
    );
  };

  public render() {
    const { serverGroup, dismissModal } = this.props;
    const { desired, taskMonitor } = this.state;
    const currentSize = serverGroup.instances?.length ?? 0;

    return (
      <>
        <TaskMonitorWrapper monitor={taskMonitor} />
        <Modal.Header>
          <Modal.Title>Resize {serverGroup.name}</Modal.Title>
        </Modal.Header>
        <ModalClose dismiss={dismissModal} />
        <Modal.Body>
          <div className="form-horizontal">
            <div className="form-group">
              <div className="col-md-3 sm-label-right">Current size</div>
              <div className="col-md-4">
                <div className="horizontal middle">
                  <input type="number" className="NumberInput form-control" value={currentSize} disabled={true} />
                  <div className="sp-padding-xs-xaxis">instances</div>
                </div>
              </div>
            </div>
            <div className="form-group">
              <div className="col-md-3 sm-label-right">Resize to</div>
              <div className="col-md-4">
                <div className="horizontal middle">
                  <input
                    type="number"
                    className="NumberInput form-control"
                    min={0}
                    value={desired}
                    onChange={(e) => this.setState({ desired: Number(e.target.value) })}
                  />
                  <div className="sp-padding-xs-xaxis">instances</div>
                </div>
              </div>
            </div>
            <div className="form-group">
              <div className="col-md-offset-3 col-md-8">
                <p className="form-control-static small text-muted">
                  Additional instances are cloned from the template the group was deployed from; when shrinking, the
                  newest instances are removed first.
                </p>
              </div>
            </div>
          </div>
        </Modal.Body>
        <ModalFooter>
          <button className="btn btn-default" onClick={dismissModal}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={this.submit} disabled={desired === currentSize || desired < 0}>
            Submit
          </button>
        </ModalFooter>
      </>
    );
  }
}
