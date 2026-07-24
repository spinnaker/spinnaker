import { get } from 'lodash';
import React from 'react';
import { Modal, ModalFooter } from 'react-bootstrap';

import type { Application, DeckRuntimeServices, IModalComponentProps, IServerGroupJob } from '@spinnaker/core';
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
} from '@spinnaker/core';

interface ITitusRollbackServerGroupModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: any;
  previousServerGroup: any;
  allServerGroups: any[];
}

interface ITitusRollbackServerGroupModalState {
  initialValues: ITitusRollbackValues;
  taskMonitor: TaskMonitor;
  verified: boolean;
}

interface ITitusRollbackValues {
  delayBeforeDisableSeconds: number;
  interestingHealthProviderNames?: string[];
  reason?: string;
  restoreServerGroupName?: string;
  targetHealthyRollbackPercentage: number;
}

interface ITitusRollbackJob extends IServerGroupJob {
  rollbackType: string;
  rollbackContext: {
    delayBeforeDisableSeconds: number;
    imageId?: string;
    rollbackServerGroupName: string;
    restoreServerGroupName?: string;
    targetHealthyRollbackPercentage: number;
  };
  interestingHealthProviderNames?: string[];
  platformHealthOnlyShowOverride?: boolean;
  reason?: string;
  securityGroups?: string[];
  targetGroups?: string[];
}

export class TitusRollbackServerGroupModal extends React.Component<
  ITitusRollbackServerGroupModalProps,
  ITitusRollbackServerGroupModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public static defaultProps: Partial<ITitusRollbackServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(
    props: ITitusRollbackServerGroupModalProps,
    runtimeServices: DeckRuntimeServices,
  ): Promise<ITitusRollbackJob> {
    return ReactModal.show(TitusRollbackServerGroupModal, props, undefined, runtimeServices);
  }

  constructor(props: ITitusRollbackServerGroupModalProps) {
    super(props);

    this.state = {
      initialValues: {
        delayBeforeDisableSeconds: 0,
        interestingHealthProviderNames: this.getInitialHealthProviders(),
        restoreServerGroupName: props.previousServerGroup?.name,
        targetHealthyRollbackPercentage: this.getDefaultHealthyPercent(),
      },
      taskMonitor: new TaskMonitor({
        application: props.application,
        title: 'Rollback ' + props.serverGroup.name,
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: () => this.props.application.serverGroups.refresh(),
      }),
      verified: false,
    };
  }

  private get rollbackType(): string {
    return this.props.allServerGroups.length === 0 && this.previousImageServerGroup ? 'PREVIOUS_IMAGE' : 'EXPLICIT';
  }

  private get previousImageServerGroup(): any {
    const previousServerGroup = get(
      this.props.serverGroup,
      'entityTags.creationMetadata.value.previousServerGroup',
    ) as any;
    if (!previousServerGroup) {
      return null;
    }
    return {
      imageId:
        previousServerGroup.imageId && previousServerGroup.imageId !== previousServerGroup.imageName
          ? previousServerGroup.imageId
          : undefined,
      imageName: previousServerGroup.imageName,
      name: previousServerGroup.name,
    };
  }

  private close = (): void => {
    this.props.dismissModal();
  };

  private getDefaultHealthyPercent(): number {
    const desired = Number(this.props.serverGroup.capacity.desired);
    if (desired < 10) {
      return 100;
    }
    return desired < 20 ? 90 : 95;
  }

  private getInitialHealthProviders(): string[] {
    const { application } = this.props;
    if (application.attributes?.platformHealthOnlyShowOverride && application.attributes?.platformHealthOnly) {
      return ['Titus'];
    }
    return undefined;
  }

  private isValid(values: ITitusRollbackValues): boolean {
    if (!this.state.verified) {
      return false;
    }
    return this.rollbackType === 'PREVIOUS_IMAGE' || values.restoreServerGroupName !== undefined;
  }

  private label(serverGroup: any): string {
    if (!serverGroup) {
      return '';
    }
    const image = serverGroup.buildInfo?.images?.[0];
    if (!image) {
      return serverGroup.name;
    }
    const imageName = image.includes('/') ? image.substring(image.indexOf('/') + 1) : image;
    return `${serverGroup.name} (${imageName})`;
  }

  private minHealthy(percent: number): number {
    return Math.ceil((Number(this.props.serverGroup.capacity.desired) * percent) / 100);
  }

  private submit = (values: ITitusRollbackValues): void => {
    if (!this.isValid(values)) {
      return;
    }

    const { application, previousServerGroup, serverGroup } = this.props;
    const previousImageServerGroup = this.previousImageServerGroup;
    const command: ITitusRollbackJob = {
      rollbackType: this.rollbackType,
      rollbackContext: {
        delayBeforeDisableSeconds: values.delayBeforeDisableSeconds,
        imageId: previousServerGroup ? previousServerGroup.imageId : previousImageServerGroup?.imageId,
        rollbackServerGroupName: serverGroup.name,
        restoreServerGroupName: previousServerGroup ? previousServerGroup.name : values.restoreServerGroupName,
        targetHealthyRollbackPercentage: values.targetHealthyRollbackPercentage,
      },
      interestingHealthProviderNames: values.interestingHealthProviderNames,
      platformHealthOnlyShowOverride: application.attributes?.platformHealthOnlyShowOverride,
      reason: values.reason,
      securityGroups: serverGroup.securityGroups,
      targetGroups: serverGroup.targetGroups,
    };

    this.state.taskMonitor.submit(() =>
      this.context.services.serverGroupWriter.rollbackServerGroup(serverGroup, application, command),
    );
  };

  public render(): JSX.Element {
    const { allServerGroups, application, serverGroup } = this.props;
    const previousImageServerGroup = this.previousImageServerGroup;
    const previousServerGroupLabel = this.label({ name: this.state.initialValues.restoreServerGroupName });

    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <SpinFormik<ITitusRollbackValues>
          initialValues={this.state.initialValues}
          onSubmit={this.submit}
          render={(formik) => (
            <>
              <ModalClose dismiss={this.close} />
              <Modal.Header>
                <Modal.Title>Rollback {serverGroup.name}</Modal.Title>
              </Modal.Header>
              <Modal.Body>
                <form className="form-horizontal">
                  <div className="row">
                    <div className="col-sm-3 sm-label-right">Restore to</div>
                    <div className="col-sm-7">
                      {this.rollbackType === 'EXPLICIT' && (
                        <FormikFormField
                          name="restoreServerGroupName"
                          fastField={true}
                          input={(props) => (
                            <ReactSelectInput
                              {...props}
                              clearable={false}
                              stringOptions={allServerGroups.map((sg) => sg.name)}
                            />
                          )}
                          required={true}
                        />
                      )}
                      {this.rollbackType === 'PREVIOUS_IMAGE' && previousImageServerGroup && (
                        <div style={{ marginTop: '5px' }}>
                          {previousImageServerGroup.name} <span className="small">(no longer deployed)</span>
                          <br />
                          <span className="small">
                            <strong>Image</strong>: {previousImageServerGroup.imageName}
                            {previousImageServerGroup.imageId && <> ({previousImageServerGroup.imageId})</>}
                          </span>
                        </div>
                      )}
                    </div>
                  </div>

                  {application.attributes?.platformHealthOnlyShowOverride && (
                    <div className="row">
                      <div className="col-sm-10 col-sm-offset-1">
                        <PlatformHealthOverride
                          interestingHealthProviderNames={formik.values.interestingHealthProviderNames}
                          onChange={(value) => formik.setFieldValue('interestingHealthProviderNames', value)}
                          platformHealthType="Titus"
                          showHelpDetails={true}
                        />
                      </div>
                    </div>
                  )}

                  <TaskReason
                    reason={formik.values.reason}
                    onChange={(value) => formik.setFieldValue('reason', value)}
                  />

                  <div className="row">
                    <div className="col-sm-11 col-sm-offset-1">
                      Wait{' '}
                      <input
                        className="form-control input-sm inline-number"
                        min="0"
                        onChange={(event) =>
                          formik.setFieldValue('delayBeforeDisableSeconds', Number(event.target.value))
                        }
                        placeholder="0"
                        type="number"
                        value={formik.values.delayBeforeDisableSeconds}
                      />{' '}
                      seconds before disabling <em>{this.label(serverGroup)}</em>.
                    </div>
                  </div>

                  <div className="row">
                    <div className="col-sm-11 col-sm-offset-1">
                      Consider rollback successful when{' '}
                      <input
                        className="form-control input-sm inline-number"
                        max="100"
                        min="0"
                        onChange={(event) =>
                          formik.setFieldValue('targetHealthyRollbackPercentage', Number(event.target.value))
                        }
                        type="number"
                        value={formik.values.targetHealthyRollbackPercentage}
                      />{' '}
                      percent of instances are healthy.
                    </div>
                  </div>
                </form>

                <div className="row">
                  <div className="col-sm-4 sm-label-right">Rollback Operations</div>
                </div>
                {this.rollbackType === 'EXPLICIT' && (
                  <div className="row">
                    <div className="col-sm-11 col-sm-offset-1">
                      <ol>
                        <li>
                          Enable <em>{formik.values.restoreServerGroupName || 'previous server group'}</em>
                        </li>
                        <li>
                          Resize <em>{formik.values.restoreServerGroupName || previousServerGroupLabel}</em> to [
                          <strong>min</strong>: {serverGroup.capacity.desired}, <strong>max</strong>:{' '}
                          {serverGroup.capacity.max}, <strong>desired</strong>: {serverGroup.capacity.desired}]
                        </li>
                        {formik.values.targetHealthyRollbackPercentage < 100 && (
                          <li>
                            Wait for at least {this.minHealthy(formik.values.targetHealthyRollbackPercentage)} instances
                            to report as healthy
                          </li>
                        )}
                        <li>Disable {serverGroup.name}</li>
                        <li>
                          Restore minimum capacity of{' '}
                          <em>{formik.values.restoreServerGroupName || 'previous server group'}</em> [
                          <strong>min</strong>: {serverGroup.capacity.min}]
                        </li>
                      </ol>
                      <p>
                        This rollback will affect server groups in {serverGroup.account} ({serverGroup.region}).
                      </p>
                    </div>
                  </div>
                )}
                {this.rollbackType === 'PREVIOUS_IMAGE' && previousImageServerGroup && (
                  <div className="row">
                    <div className="col-sm-11 col-sm-offset-1">
                      <ol>
                        <li>
                          Deploy <em>{previousImageServerGroup.imageId || previousImageServerGroup.imageName}</em> [
                          <strong>min</strong>: {serverGroup.capacity.desired}, <strong>max</strong>:{' '}
                          {serverGroup.capacity.max}, <strong>desired</strong>: {serverGroup.capacity.desired}]
                        </li>
                        {formik.values.targetHealthyRollbackPercentage < 100 && (
                          <li>
                            Wait for at least {this.minHealthy(formik.values.targetHealthyRollbackPercentage)} instances
                            to report as healthy
                          </li>
                        )}
                        <li>Disable {serverGroup.name}</li>
                        <li>
                          Restore minimum capacity of <em>new server group</em> [<strong>min</strong>:{' '}
                          {serverGroup.capacity.min}]
                        </li>
                      </ol>
                      <p>
                        This rollback will affect server groups in {serverGroup.account} ({serverGroup.region}).
                      </p>
                    </div>
                  </div>
                )}
                <UserVerification
                  account={serverGroup.account}
                  onValidChange={(verified) => this.setState({ verified })}
                />
              </Modal.Body>
              <ModalFooter>
                <button className="btn btn-default" onClick={this.close} type="button">
                  Cancel
                </button>
                <button
                  className="btn btn-primary"
                  disabled={!this.isValid(formik.values)}
                  onClick={() => this.submit(formik.values)}
                  type="button"
                >
                  Submit
                </button>
              </ModalFooter>
            </>
          )}
        />
      </>
    );
  }
}
