import React from 'react';
import { Modal } from 'react-bootstrap';

import type {
  Application,
  DeckRuntimeServices,
  IModalComponentProps,
  IServerGroup,
  IServerGroupJob,
} from '@spinnaker/core';
import {
  DeckRuntimeContext,
  FormikFormField,
  ModalClose,
  noop,
  PlatformHealthOverride,
  ReactModal,
  ReactSelectInput,
  SpinFormik,
  TaskMonitor,
  TaskMonitorWrapper,
  TaskReason,
  UserVerification,
  ValidationMessage,
} from '@spinnaker/core';

export interface IEcsRollbackServerGroupModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: IServerGroup;
}

export interface IEcsRollbackServerGroupValues {
  interestingHealthProviderNames?: string[];
  reason?: string;
  restoreServerGroupName?: string;
  targetHealthyRollbackPercentage: number;
}

export interface IEcsRollbackServerGroupJob extends IServerGroupJob {
  interestingHealthProviderNames?: string[];
  platformHealthOnlyShowOverride?: boolean;
  reason?: string;
  rollbackContext: {
    restoreServerGroupName: string;
    rollbackServerGroupName: string;
    targetHealthyRollbackPercentage: number;
  };
  rollbackType: 'EXPLICIT';
}

export interface IEcsRollbackServerGroupErrors {
  restoreServerGroupName?: string;
  targetHealthyRollbackPercentage?: string;
}

interface IEcsRollbackServerGroupModalState {
  initialValues: IEcsRollbackServerGroupValues;
  taskMonitor: TaskMonitor;
  verified: boolean;
}

function coordinate(serverGroup: any, key: 'account' | 'cluster'): string | undefined {
  if (key === 'account') {
    return serverGroup.account || serverGroup.accountId;
  }
  return serverGroup.cluster || serverGroup.moniker?.cluster;
}

export function getEcsRollbackTargets(application: Application, serverGroup: IServerGroup): IServerGroup[] {
  const app = application as any;
  const appName = (serverGroup as any).moniker?.app || app.name;
  const candidates: IServerGroup[] = app.serverGroups?.data || app.getDataSource?.('serverGroups')?.data || [];

  return candidates
    .filter((candidate: any) => {
      const candidateApp = candidate.moniker?.app || candidate.application || appName;
      return (
        candidate.isDisabled &&
        candidateApp === appName &&
        coordinate(candidate, 'cluster') === coordinate(serverGroup, 'cluster') &&
        coordinate(candidate, 'account') === coordinate(serverGroup, 'account') &&
        candidate.region === serverGroup.region
      );
    })
    .sort((a, b) => b.name.localeCompare(a.name));
}

export function validateEcsRollbackValues(
  values: IEcsRollbackServerGroupValues,
  eligibleTargetNames?: string[],
): IEcsRollbackServerGroupErrors {
  const errors: IEcsRollbackServerGroupErrors = {};
  if (!values.restoreServerGroupName) {
    errors.restoreServerGroupName = 'Select a server group to restore';
  } else if (eligibleTargetNames && !eligibleTargetNames.includes(values.restoreServerGroupName)) {
    errors.restoreServerGroupName = 'Select an eligible server group to restore';
  }
  if (
    !Number.isFinite(values.targetHealthyRollbackPercentage) ||
    values.targetHealthyRollbackPercentage < 0 ||
    values.targetHealthyRollbackPercentage > 100
  ) {
    errors.targetHealthyRollbackPercentage = 'Healthy threshold must be between 0 and 100';
  }
  return errors;
}

export class EcsRollbackServerGroupModal extends React.Component<
  IEcsRollbackServerGroupModalProps,
  IEcsRollbackServerGroupModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public static defaultProps: Partial<IEcsRollbackServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(
    props: IEcsRollbackServerGroupModalProps,
    runtimeServices: DeckRuntimeServices,
  ): Promise<IEcsRollbackServerGroupJob> {
    return ReactModal.show(EcsRollbackServerGroupModal, props, undefined, runtimeServices);
  }

  public constructor(props: IEcsRollbackServerGroupModalProps) {
    super(props);
    const { application, serverGroup } = props;
    const attributes = application.attributes || {};
    this.state = {
      initialValues: {
        interestingHealthProviderNames:
          attributes.platformHealthOnlyShowOverride && attributes.platformHealthOnly ? ['Ecs'] : undefined,
        targetHealthyRollbackPercentage: 100,
      },
      taskMonitor: new TaskMonitor({
        application,
        title: `Rollback ${serverGroup.name}`,
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: () => application.serverGroups.refresh(),
      }),
      verified: false,
    };
  }

  private close = (): void => this.props.dismissModal();

  private submit = (values: IEcsRollbackServerGroupValues): void => {
    const eligibleTargetNames = getEcsRollbackTargets(this.props.application, this.props.serverGroup).map(
      ({ name }) => name,
    );
    if (!this.state.verified || Object.keys(validateEcsRollbackValues(values, eligibleTargetNames)).length > 0) {
      return;
    }

    const { application, serverGroup } = this.props;
    const command: IEcsRollbackServerGroupJob = {
      interestingHealthProviderNames: values.interestingHealthProviderNames,
      platformHealthOnlyShowOverride: application.attributes?.platformHealthOnlyShowOverride,
      reason: values.reason,
      rollbackContext: {
        restoreServerGroupName: values.restoreServerGroupName,
        rollbackServerGroupName: serverGroup.name,
        targetHealthyRollbackPercentage: values.targetHealthyRollbackPercentage,
      },
      rollbackType: 'EXPLICIT',
    };

    this.state.taskMonitor.submit(() =>
      this.context.services.serverGroupWriter.rollbackServerGroup(serverGroup, application, command),
    );
  };

  public render(): JSX.Element {
    const { application, serverGroup } = this.props;
    const targets = getEcsRollbackTargets(application, serverGroup);

    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <SpinFormik<IEcsRollbackServerGroupValues>
          initialValues={this.state.initialValues}
          onSubmit={this.submit}
          validate={(values) =>
            validateEcsRollbackValues(
              values,
              targets.map(({ name }) => name),
            )
          }
          render={(formik) => (
            <>
              <ModalClose dismiss={this.close} />
              <Modal.Header>
                <Modal.Title>Rollback {serverGroup.name}</Modal.Title>
              </Modal.Header>
              <Modal.Body>
                <form className="form-horizontal">
                  <div className="form-group">
                    <div className="col-sm-3 sm-label-right">Restore to</div>
                    <div className="col-sm-7">
                      <FormikFormField
                        name="restoreServerGroupName"
                        fastField={true}
                        input={(props) => (
                          <ReactSelectInput
                            {...props}
                            clearable={false}
                            stringOptions={targets.map(({ name }) => name)}
                          />
                        )}
                        required={true}
                      />
                    </div>
                  </div>

                  {application.attributes?.platformHealthOnlyShowOverride && (
                    <div className="form-group">
                      <div className="col-sm-10 col-sm-offset-1">
                        <PlatformHealthOverride
                          interestingHealthProviderNames={formik.values.interestingHealthProviderNames}
                          onChange={(names) => formik.setFieldValue('interestingHealthProviderNames', names)}
                          platformHealthType="Ecs"
                          showHelpDetails={true}
                        />
                      </div>
                    </div>
                  )}

                  <TaskReason
                    reason={formik.values.reason}
                    onChange={(reason) => formik.setFieldValue('reason', reason)}
                  />

                  <div className="form-group">
                    <div className="col-sm-3 sm-label-right">Healthy threshold</div>
                    <div className="col-sm-3">
                      <input
                        className="form-control input-sm"
                        max={100}
                        min={0}
                        name="targetHealthyRollbackPercentage"
                        onChange={(event) =>
                          formik.setFieldValue('targetHealthyRollbackPercentage', Number(event.target.value))
                        }
                        type="number"
                        value={formik.values.targetHealthyRollbackPercentage}
                      />
                    </div>
                    <div className="col-sm-2 sm-control-field">percent</div>
                  </div>
                  {formik.errors.targetHealthyRollbackPercentage && (
                    <div className="col-sm-7 col-sm-offset-3">
                      <ValidationMessage
                        message={formik.errors.targetHealthyRollbackPercentage as string}
                        type="error"
                      />
                    </div>
                  )}
                </form>
              </Modal.Body>
              <Modal.Footer>
                <UserVerification
                  account={serverGroup.account}
                  onValidChange={(verified) => this.setState({ verified })}
                />
                <button className="btn btn-default" onClick={this.close} type="button">
                  Cancel
                </button>
                <button
                  className="btn btn-primary"
                  disabled={!this.state.verified || !formik.isValid}
                  onClick={() => this.submit(formik.values)}
                  type="submit"
                >
                  Submit
                </button>
              </Modal.Footer>
            </>
          )}
        />
      </>
    );
  }
}
