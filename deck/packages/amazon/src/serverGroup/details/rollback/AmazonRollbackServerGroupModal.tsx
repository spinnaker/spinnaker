import React from 'react';
import { Modal } from 'react-bootstrap';

import type { Application, DeckRuntimeServices, IModalComponentProps, IServerGroupJob } from '@spinnaker/core';
import {
  DeckRuntimeContext,
  ModalClose,
  noop,
  PlatformHealthOverride,
  ReactModal,
  SpinFormik,
  TaskMonitor,
  TaskMonitorWrapper,
  TaskReason,
  UserVerification,
  ValidationMessage,
} from '@spinnaker/core';

import type { IAmazonServerGroup } from '../../../domain';

export type AmazonRollbackType = 'EXPLICIT' | 'PREVIOUS_IMAGE';

export interface IAmazonPreviousImageServerGroup {
  buildNumber?: number | string;
  imageId?: string;
  imageName: string;
  name: string;
}

export interface IAmazonRollbackValues {
  delayBeforeDisableSeconds: number;
  interestingHealthProviderNames?: string[];
  reason?: string;
  restoreServerGroupName?: string;
  targetHealthyRollbackPercentage: number;
}

export interface IAmazonRollbackJob extends IServerGroupJob {
  interestingHealthProviderNames?: string[];
  platformHealthOnlyShowOverride?: boolean;
  reason?: string;
  rollbackContext: {
    delayBeforeDisableSeconds: number;
    restoreServerGroupName?: string;
    rollbackServerGroupName: string;
    targetHealthyRollbackPercentage: number;
  };
  rollbackType: AmazonRollbackType;
}

export interface IAmazonRollbackErrors {
  delayBeforeDisableSeconds?: string;
  restoreServerGroupName?: string;
  targetHealthyRollbackPercentage?: string;
}

export interface IAmazonRollbackServerGroupModalProps extends IModalComponentProps {
  allServerGroups: IAmazonServerGroup[];
  application: Application;
  previousServerGroup?: IAmazonServerGroup;
  serverGroup: IAmazonServerGroup;
}

interface IAmazonRollbackServerGroupModalState {
  initialValues: IAmazonRollbackValues;
  taskMonitor: TaskMonitor;
  verified: boolean;
}

export function getDefaultAmazonHealthyRollbackPercentage(desired: number): number {
  if (desired < 10) {
    return 100;
  }
  return desired < 20 ? 90 : 95;
}

export function getAmazonPreviousImageServerGroup(
  serverGroup: IAmazonServerGroup,
): IAmazonPreviousImageServerGroup | undefined {
  const previousServerGroup = (serverGroup.entityTags as any)?.creationMetadata?.value?.previousServerGroup;
  if (!previousServerGroup) {
    return undefined;
  }

  const buildNumber = previousServerGroup.buildInfo?.jenkins?.number;
  return {
    ...(buildNumber !== undefined ? { buildNumber } : {}),
    imageId:
      previousServerGroup.imageId && previousServerGroup.imageId !== previousServerGroup.imageName
        ? previousServerGroup.imageId
        : undefined,
    imageName: previousServerGroup.imageName,
    name: previousServerGroup.name,
  };
}

export function getAmazonRollbackType(
  serverGroup: IAmazonServerGroup,
  allServerGroups: IAmazonServerGroup[],
): AmazonRollbackType {
  return allServerGroups.length === 0 && getAmazonPreviousImageServerGroup(serverGroup) ? 'PREVIOUS_IMAGE' : 'EXPLICIT';
}

export function validateAmazonRollbackValues(
  values: IAmazonRollbackValues,
  rollbackType: AmazonRollbackType,
  eligibleTargetNames?: string[],
): IAmazonRollbackErrors {
  const errors: IAmazonRollbackErrors = {};

  if (rollbackType === 'EXPLICIT') {
    if (!values.restoreServerGroupName) {
      errors.restoreServerGroupName = 'Select a server group to restore';
    } else if (eligibleTargetNames && !eligibleTargetNames.includes(values.restoreServerGroupName)) {
      errors.restoreServerGroupName = 'Select an eligible server group to restore';
    }
  }

  if (
    !Number.isFinite(values.targetHealthyRollbackPercentage) ||
    values.targetHealthyRollbackPercentage < 0 ||
    values.targetHealthyRollbackPercentage > 100
  ) {
    errors.targetHealthyRollbackPercentage = 'Healthy threshold must be between 0 and 100';
  }

  if (
    !Number.isFinite(values.delayBeforeDisableSeconds) ||
    !Number.isInteger(values.delayBeforeDisableSeconds) ||
    values.delayBeforeDisableSeconds < 0
  ) {
    errors.delayBeforeDisableSeconds = 'Delay must be a non-negative whole number';
  }

  return errors;
}

export function buildAmazonRollbackJob(
  application: Application,
  serverGroup: IAmazonServerGroup,
  rollbackType: AmazonRollbackType,
  values: IAmazonRollbackValues,
): IAmazonRollbackJob {
  return {
    interestingHealthProviderNames: values.interestingHealthProviderNames,
    platformHealthOnlyShowOverride: application.attributes?.platformHealthOnlyShowOverride,
    reason: values.reason,
    rollbackContext: {
      delayBeforeDisableSeconds: values.delayBeforeDisableSeconds,
      restoreServerGroupName: values.restoreServerGroupName,
      rollbackServerGroupName: serverGroup.name,
      targetHealthyRollbackPercentage: values.targetHealthyRollbackPercentage,
    },
    rollbackType,
  };
}

export class AmazonRollbackServerGroupModal extends React.Component<
  IAmazonRollbackServerGroupModalProps,
  IAmazonRollbackServerGroupModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public static defaultProps: Partial<IAmazonRollbackServerGroupModalProps> = {
    allServerGroups: [],
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(
    props: IAmazonRollbackServerGroupModalProps,
    runtimeServices: DeckRuntimeServices,
  ): Promise<IAmazonRollbackJob> {
    return ReactModal.show(AmazonRollbackServerGroupModal, props, undefined, runtimeServices);
  }

  public constructor(props: IAmazonRollbackServerGroupModalProps) {
    super(props);
    const { application, previousServerGroup, serverGroup } = props;
    const desired = Number(serverGroup.capacity.desired);
    this.state = {
      initialValues: {
        delayBeforeDisableSeconds: 0,
        interestingHealthProviderNames:
          application.attributes?.platformHealthOnlyShowOverride && application.attributes?.platformHealthOnly
            ? ['Amazon']
            : undefined,
        restoreServerGroupName: previousServerGroup?.name,
        targetHealthyRollbackPercentage: getDefaultAmazonHealthyRollbackPercentage(desired),
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

  private get rollbackType(): AmazonRollbackType {
    return getAmazonRollbackType(this.props.serverGroup, this.props.allServerGroups);
  }

  private close = (): void => this.props.dismissModal();

  private label(serverGroup: IAmazonServerGroup): string {
    const buildNumber = serverGroup?.buildInfo?.jenkins?.number;
    return buildNumber ? `${serverGroup.name} (build #${buildNumber})` : serverGroup?.name || '';
  }

  private minHealthy(percent: number): number {
    return Math.ceil((Number(this.props.serverGroup.capacity.desired) * percent) / 100);
  }

  private submit = (values: IAmazonRollbackValues): void => {
    const { allServerGroups, application, serverGroup } = this.props;
    const errors = validateAmazonRollbackValues(
      values,
      this.rollbackType,
      allServerGroups.map(({ name }) => name),
    );
    if (!this.state.verified || Object.keys(errors).length > 0) {
      return;
    }

    const command = buildAmazonRollbackJob(application, serverGroup, this.rollbackType, values);
    this.state.taskMonitor.submit(() =>
      this.context.services.serverGroupWriter.rollbackServerGroup(serverGroup, application, command),
    );
  };

  private renderRestoreTarget(values: IAmazonRollbackValues, setFieldValue: (name: string, value: any) => void) {
    if (this.rollbackType === 'PREVIOUS_IMAGE') {
      const previousImage = getAmazonPreviousImageServerGroup(this.props.serverGroup);
      return (
        <div style={{ marginTop: '5px' }}>
          {previousImage.name} <span className="small">(no longer deployed)</span>
          <br />
          <span className="small">
            <strong>Image</strong>: {previousImage.imageName}
            {previousImage.imageId && <> ({previousImage.imageId})</>}
          </span>
          {previousImage.buildNumber !== undefined && (
            <>
              <br />
              <span className="small">
                <strong>Build</strong> #{previousImage.buildNumber}
              </span>
            </>
          )}
        </div>
      );
    }

    const enabled = this.props.allServerGroups.filter(({ isDisabled }) => !isDisabled);
    const disabled = this.props.allServerGroups.filter(({ isDisabled }) => isDisabled);
    const options = (serverGroups: IAmazonServerGroup[]) =>
      serverGroups.map((candidate) => (
        <option key={candidate.name} value={candidate.name}>
          {this.label(candidate)}
        </option>
      ));
    return (
      <select
        className="form-control input-sm"
        name="restoreServerGroupName"
        onChange={(event) => setFieldValue('restoreServerGroupName', event.target.value || undefined)}
        value={values.restoreServerGroupName || ''}
      >
        <option value="">Select...</option>
        {enabled.length > 0 && <optgroup label="Enabled Server Groups">{options(enabled)}</optgroup>}
        {disabled.length > 0 && <optgroup label="Disabled Server Groups">{options(disabled)}</optgroup>}
      </select>
    );
  }

  private renderOperations(values: IAmazonRollbackValues): JSX.Element {
    const { serverGroup } = this.props;
    const previousImage = getAmazonPreviousImageServerGroup(serverGroup);
    const restoreName = values.restoreServerGroupName || 'previous server group';
    return (
      <div className="row">
        <div className="col-sm-11 col-sm-offset-1">
          <ol>
            {this.rollbackType === 'EXPLICIT' ? (
              <>
                <li>
                  Enable <em>{restoreName}</em>
                </li>
                <li>
                  Resize <em>{restoreName}</em> to [<strong>min</strong>: {serverGroup.capacity.desired},{' '}
                  <strong>max</strong>: {serverGroup.capacity.max}, <strong>desired</strong>:{' '}
                  {serverGroup.capacity.desired}]
                </li>
              </>
            ) : (
              <li>
                Deploy <em>{previousImage.imageId || previousImage.imageName}</em> [<strong>min</strong>:{' '}
                {serverGroup.capacity.desired}, <strong>max</strong>: {serverGroup.capacity.max},{' '}
                <strong>desired</strong>: {serverGroup.capacity.desired}]
              </li>
            )}
            {values.targetHealthyRollbackPercentage < 100 && (
              <li>
                Wait for at least {this.minHealthy(values.targetHealthyRollbackPercentage)} instances to report as
                healthy
              </li>
            )}
            {values.delayBeforeDisableSeconds > 0 && <li>Wait {values.delayBeforeDisableSeconds} seconds</li>}
            <li>
              Disable <em>{serverGroup.name}</em>
            </li>
            <li>
              Restore minimum capacity of <em>{this.rollbackType === 'EXPLICIT' ? restoreName : 'new server group'}</em>{' '}
              [<strong>min</strong>: {serverGroup.capacity.min}]
            </li>
          </ol>
          <p>
            This rollback will affect server groups in {serverGroup.account} ({serverGroup.region}).
          </p>
        </div>
      </div>
    );
  }

  public render(): JSX.Element {
    const { application, serverGroup } = this.props;
    const eligibleTargetNames = this.props.allServerGroups.map(({ name }) => name);
    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <SpinFormik<IAmazonRollbackValues>
          initialValues={this.state.initialValues}
          onSubmit={this.submit}
          validate={(values) => validateAmazonRollbackValues(values, this.rollbackType, eligibleTargetNames)}
          render={(formik) => (
            <>
              <ModalClose dismiss={this.close} />
              <Modal.Header>
                <Modal.Title>Rollback {serverGroup.name}</Modal.Title>
              </Modal.Header>
              <Modal.Body>
                <form className="form-horizontal">
                  <div className="form-group row">
                    <div className="col-sm-3 sm-label-right">Restore to</div>
                    <div className="col-md-7">
                      {this.renderRestoreTarget(formik.values, formik.setFieldValue)}
                      {formik.errors.restoreServerGroupName && (
                        <ValidationMessage message={formik.errors.restoreServerGroupName as string} type="error" />
                      )}
                    </div>
                  </div>

                  {application.attributes?.platformHealthOnlyShowOverride && (
                    <div className="form-group row">
                      <div className="col-sm-10 col-sm-offset-1">
                        <PlatformHealthOverride
                          interestingHealthProviderNames={formik.values.interestingHealthProviderNames}
                          onChange={(names) => formik.setFieldValue('interestingHealthProviderNames', names)}
                          platformHealthType="Amazon"
                          showHelpDetails={true}
                        />
                      </div>
                    </div>
                  )}

                  <TaskReason
                    reason={formik.values.reason}
                    onChange={(reason) => formik.setFieldValue('reason', reason)}
                  />

                  <div className="form-group row">
                    <div className="col-sm-11 col-sm-offset-1">
                      Wait{' '}
                      <input
                        className="form-control input-sm inline-number"
                        min={0}
                        name="delayBeforeDisableSeconds"
                        onChange={(event) =>
                          formik.setFieldValue('delayBeforeDisableSeconds', Number(event.target.value))
                        }
                        placeholder="0"
                        step={1}
                        type="number"
                        value={formik.values.delayBeforeDisableSeconds}
                      />{' '}
                      seconds before disabling <em>{this.label(serverGroup)}</em>.
                      {formik.errors.delayBeforeDisableSeconds && (
                        <ValidationMessage message={formik.errors.delayBeforeDisableSeconds as string} type="error" />
                      )}
                    </div>
                  </div>

                  <div className="form-group row">
                    <div className="col-sm-11 col-sm-offset-1">
                      Consider rollback successful when{' '}
                      <input
                        className="form-control input-sm inline-number"
                        max={100}
                        min={0}
                        name="targetHealthyRollbackPercentage"
                        onChange={(event) =>
                          formik.setFieldValue('targetHealthyRollbackPercentage', Number(event.target.value))
                        }
                        type="number"
                        value={formik.values.targetHealthyRollbackPercentage}
                      />{' '}
                      percent of instances are healthy.
                      {formik.errors.targetHealthyRollbackPercentage && (
                        <ValidationMessage
                          message={formik.errors.targetHealthyRollbackPercentage as string}
                          type="error"
                        />
                      )}
                    </div>
                  </div>
                </form>

                <div className="row">
                  <div className="col-sm-4 sm-label-right">Rollback Operations</div>
                </div>
                {this.renderOperations(formik.values)}
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
                  type="button"
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
