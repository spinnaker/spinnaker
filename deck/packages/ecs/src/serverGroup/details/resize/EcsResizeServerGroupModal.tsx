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
  MinMaxDesiredChanges,
  ModalClose,
  noop,
  NumberInput,
  PlatformHealthOverride,
  ReactModal,
  SpinFormik,
  TaskMonitor,
  TaskMonitorWrapper,
  TaskReason,
  UserVerification,
  ValidationMessage,
} from '@spinnaker/core';

export interface IEcsResizeServerGroupModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: IServerGroup;
}

export interface IEcsResizeCapacity {
  desired: number;
  max: number;
  min: number;
}

export interface IEcsResizeServerGroupValues {
  capacity: IEcsResizeCapacity;
  interestingHealthProviderNames?: string[];
  reason?: string;
}

export interface IEcsResizeServerGroupJob extends IServerGroupJob {
  capacity: IEcsResizeCapacity;
  interestingHealthProviderNames?: string[];
  reason?: string;
}

export interface IEcsResizeServerGroupErrors {
  capacity?: Partial<Record<keyof IEcsResizeCapacity, string>>;
}

interface IEcsResizeServerGroupModalState {
  initialValues: IEcsResizeServerGroupValues;
  taskMonitor: TaskMonitor;
  verified: boolean;
}

export function validateEcsResizeValues(values: IEcsResizeServerGroupValues): IEcsResizeServerGroupErrors {
  const { desired, max, min } = values.capacity;
  const capacity: IEcsResizeServerGroupErrors['capacity'] = {};

  if (!Number.isFinite(min) || min < 0) {
    capacity.min = 'Min cannot be negative';
  }
  if (!Number.isFinite(max) || max < 0) {
    capacity.max = 'Max cannot be negative';
  }
  if (!Number.isFinite(desired) || desired < 0) {
    capacity.desired = 'Desired cannot be negative';
  }
  if (Object.keys(capacity).length > 0) {
    return { capacity };
  }

  if (min > max) {
    capacity.min = 'Min cannot be larger than Max';
    capacity.max = 'Max cannot be smaller than Min';
  } else if (desired < min) {
    capacity.desired = 'Desired cannot be smaller than Min';
  } else if (desired > max) {
    capacity.desired = 'Desired cannot be larger than Max';
  }

  return Object.keys(capacity).length > 0 ? { capacity } : {};
}

export class EcsResizeServerGroupModal extends React.Component<
  IEcsResizeServerGroupModalProps,
  IEcsResizeServerGroupModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public static defaultProps: Partial<IEcsResizeServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(
    props: IEcsResizeServerGroupModalProps,
    runtimeServices: DeckRuntimeServices,
  ): Promise<IEcsResizeServerGroupJob> {
    return ReactModal.show(EcsResizeServerGroupModal, props, undefined, runtimeServices);
  }

  public constructor(props: IEcsResizeServerGroupModalProps) {
    super(props);
    const { application, serverGroup } = props;
    const attributes = application.attributes || {};
    this.state = {
      initialValues: {
        capacity: {
          desired: Number(serverGroup.capacity.desired),
          max: Number(serverGroup.capacity.max),
          min: Number(serverGroup.capacity.min),
        },
        interestingHealthProviderNames:
          attributes.platformHealthOnlyShowOverride && attributes.platformHealthOnly ? ['Ecs'] : undefined,
      },
      taskMonitor: new TaskMonitor({
        application,
        title: `Resizing ${serverGroup.name}`,
        modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
        onTaskComplete: () => application.serverGroups.refresh(),
      }),
      verified: false,
    };
  }

  private close = (): void => this.props.dismissModal();

  private submit = (values: IEcsResizeServerGroupValues): void => {
    if (!this.state.verified || Object.keys(validateEcsResizeValues(values)).length > 0) {
      return;
    }

    const { application, serverGroup } = this.props;
    const command: IEcsResizeServerGroupJob = {
      capacity: values.capacity,
      interestingHealthProviderNames: values.interestingHealthProviderNames,
      reason: values.reason,
    };
    this.state.taskMonitor.submit(() =>
      this.context.services.serverGroupWriter.resizeServerGroup(serverGroup, application, command),
    );
  };

  private capacityError(errors: IEcsResizeServerGroupErrors): string | undefined {
    const capacity = errors.capacity;
    return capacity && ([capacity.min, capacity.max, capacity.desired].find(Boolean) as string);
  }

  public render(): JSX.Element {
    const { application, serverGroup } = this.props;

    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <SpinFormik<IEcsResizeServerGroupValues>
          initialValues={this.state.initialValues}
          onSubmit={this.submit}
          validate={validateEcsResizeValues}
          render={(formik) => {
            const error = this.capacityError(formik.errors as IEcsResizeServerGroupErrors);
            return (
              <>
                <ModalClose dismiss={this.close} />
                <Modal.Header>
                  <Modal.Title>Resize {serverGroup.name}</Modal.Title>
                </Modal.Header>
                <Modal.Body>
                  <form className="form-horizontal">
                    <div className="form-group bold">
                      <div className="col-sm-2 col-sm-offset-3">Min</div>
                      <div className="col-sm-2">Max</div>
                      <div className="col-sm-2">Desired</div>
                    </div>
                    <div className="form-group">
                      <div className="col-sm-3 sm-label-right">Current</div>
                      {(['min', 'max', 'desired'] as Array<keyof IEcsResizeCapacity>).map((field) => (
                        <div className="col-sm-2" key={field}>
                          <input
                            className="form-control input-sm"
                            disabled={true}
                            type="number"
                            value={serverGroup.capacity[field]}
                          />
                        </div>
                      ))}
                    </div>
                    <div className="form-group">
                      <div className="col-sm-3 sm-label-right">Resize to</div>
                      {(['min', 'max', 'desired'] as Array<keyof IEcsResizeCapacity>).map((field) => (
                        <div className="col-sm-2" key={field}>
                          <FormikFormField
                            name={`capacity.${field}`}
                            input={(props) => <NumberInput {...props} min={0} />}
                            layout={({ input }) => <>{input}</>}
                            touched={true}
                          />
                        </div>
                      ))}
                    </div>
                    {error && (
                      <div className="col-sm-9 col-sm-offset-3">
                        <ValidationMessage message={error} type="error" />
                      </div>
                    )}

                    <div className="form-group">
                      <div className="col-sm-3 sm-label-right">Changes</div>
                      <div className="col-sm-9 sm-control-field">
                        <MinMaxDesiredChanges current={serverGroup.capacity} next={formik.values.capacity} />
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
            );
          }}
        />
      </>
    );
  }
}
