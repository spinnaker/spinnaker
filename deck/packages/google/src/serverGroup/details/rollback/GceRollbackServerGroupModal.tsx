import React from 'react';

import type { Application, IModalComponentProps, IServerGroup, IServerGroupJob, ITask } from '@spinnaker/core';
import {
  ModalClose,
  noop,
  PlatformHealthOverride,
  ReactModal,
  TaskMonitor,
  TaskMonitorWrapper,
  TaskReason,
  UserVerification,
} from '@spinnaker/core';

export interface IGceRollbackValues {
  interestingHealthProviderNames?: string[];
  reason?: string;
  restoreServerGroupName?: string;
}

export interface IGceRollbackJob extends IServerGroupJob {
  interestingHealthProviderNames?: string[];
  platformHealthOnlyShowOverride?: boolean;
  reason?: string;
  rollbackContext: {
    restoreServerGroupName?: string;
    rollbackServerGroupName: string;
  };
  rollbackType: 'EXPLICIT';
}

export interface IGceRollbackServerGroupWriter {
  rollbackServerGroup(
    serverGroup: IServerGroup,
    application: Application,
    command: IGceRollbackJob,
  ): PromiseLike<ITask>;
}

export interface IGceRollbackServerGroupModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: IServerGroup;
  serverGroups: IServerGroup[];
  serverGroupWriter: IGceRollbackServerGroupWriter;
}

interface IGceRollbackServerGroupModalState {
  taskMonitor: TaskMonitor;
  values: IGceRollbackValues;
  verified: boolean;
}

export function buildGceRollbackJob(
  application: Application,
  serverGroup: IServerGroup,
  values: IGceRollbackValues,
): IGceRollbackJob {
  return {
    interestingHealthProviderNames: values.interestingHealthProviderNames,
    platformHealthOnlyShowOverride: application.attributes?.platformHealthOnlyShowOverride,
    reason: values.reason,
    rollbackContext: {
      restoreServerGroupName: values.restoreServerGroupName,
      rollbackServerGroupName: serverGroup.name,
    },
    rollbackType: 'EXPLICIT',
  };
}

export function getGceRollbackCandidates(
  application: Application,
  serverGroup: IServerGroup,
  candidates: IServerGroup[],
): IServerGroup[] {
  return candidates
    .filter((candidate) => {
      const candidateApplication = candidate.app || candidate.moniker?.app;
      return (
        candidate.isDisabled === true &&
        candidateApplication === application.name &&
        candidate.cluster === serverGroup.cluster &&
        candidate.account === serverGroup.account &&
        candidate.region === serverGroup.region
      );
    })
    .sort((a, b) => b.name.localeCompare(a.name));
}

export class GceRollbackServerGroupModal extends React.Component<
  IGceRollbackServerGroupModalProps,
  IGceRollbackServerGroupModalState
> {
  public static defaultProps: Partial<IGceRollbackServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: IGceRollbackServerGroupModalProps): Promise<IGceRollbackJob> {
    return ReactModal.show(GceRollbackServerGroupModal, props);
  }

  constructor(props: IGceRollbackServerGroupModalProps) {
    super(props);
    const { application, serverGroup } = props;
    this.state = {
      taskMonitor: new TaskMonitor({
        application,
        title: `Rollback ${serverGroup.name}`,
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: () => application.serverGroups.refresh(),
      }),
      values: {
        interestingHealthProviderNames:
          application.attributes?.platformHealthOnlyShowOverride && application.attributes?.platformHealthOnly
            ? ['Google']
            : undefined,
      },
      verified: false,
    };
  }

  private close = (): void => this.props.dismissModal();

  private isValid = (): boolean => this.state.verified && !!this.state.values.restoreServerGroupName;

  private submit = (): void => {
    if (!this.isValid()) {
      return;
    }

    const { application, serverGroup, serverGroupWriter } = this.props;
    const job = buildGceRollbackJob(application, serverGroup, this.state.values);
    this.state.taskMonitor.submit(() => serverGroupWriter.rollbackServerGroup(serverGroup, application, job));
  };

  public render(): JSX.Element {
    const { application, serverGroup, serverGroups } = this.props;
    const { taskMonitor, values } = this.state;
    const candidates = getGceRollbackCandidates(application, serverGroup, serverGroups);

    return (
      <>
        <TaskMonitorWrapper monitor={taskMonitor} />
        <ModalClose dismiss={this.close} />
        <div className="modal-header">
          <h4 className="modal-title">Rollback {serverGroup.name}</h4>
        </div>
        <div className="modal-body">
          <form className="form-horizontal">
            <div className="form-group">
              <label className="col-sm-3 sm-label-right" htmlFor="gce-rollback-server-group">
                Restore to
              </label>
              <div className="col-sm-6">
                <select
                  className="form-control input-sm"
                  id="gce-rollback-server-group"
                  onChange={(event) =>
                    this.setState({ values: { ...values, restoreServerGroupName: event.target.value || undefined } })
                  }
                  value={values.restoreServerGroupName || ''}
                >
                  <option value="">Select...</option>
                  {candidates.map((candidate) => (
                    <option key={candidate.name} value={candidate.name}>
                      {candidate.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            {application.attributes?.platformHealthOnlyShowOverride && (
              <PlatformHealthOverride
                interestingHealthProviderNames={values.interestingHealthProviderNames}
                onChange={(interestingHealthProviderNames) =>
                  this.setState({ values: { ...values, interestingHealthProviderNames } })
                }
                platformHealthType="Google"
                showHelpDetails={true}
              />
            )}
            <TaskReason
              reason={values.reason}
              onChange={(reason) => this.setState({ values: { ...values, reason } })}
            />
          </form>
        </div>
        <div className="modal-footer">
          <UserVerification account={serverGroup.account} onValidChange={(verified) => this.setState({ verified })} />
          <button className="btn btn-default" onClick={this.close} type="button">
            Cancel
          </button>
          <button className="btn btn-primary" disabled={!this.isValid()} onClick={this.submit} type="button">
            Submit
          </button>
        </div>
      </>
    );
  }
}
