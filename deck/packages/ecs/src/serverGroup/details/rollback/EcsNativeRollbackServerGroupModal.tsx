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
  ReactModal,
  ReactSelectInput,
  SpinFormik,
  TaskExecutor,
  TaskMonitor,
  TaskMonitorWrapper,
  TaskReason,
  UserVerification,
  ValidationMessage,
} from '@spinnaker/core';

/**
 * One task-definition revision of the ecs-native service's family. clouddriver attaches these to
 * the server-group details payload (EcsServerGroup.taskDefinitionRevisions) for native services, so
 * the picker reads them off the server group directly — no separate endpoint/gate route.
 */
export interface IEcsTaskDefinitionRevision {
  taskDefinitionArn: string;
  family: string;
  revision: number;
  containerImages: string[];
  current: boolean;
}

export interface IEcsNativeRollbackServerGroupModalProps extends IModalComponentProps {
  application: Application;
  serverGroup: IServerGroup;
}

export interface IEcsNativeRollbackServerGroupValues {
  reason?: string;
  taskDefinition?: string;
}

/**
 * The orca stage this submits ({@code ecsNativeRollbackServerGroup}) rolls the durable ecs-native
 * service to {@code taskDefinition} via a native in-place UpdateService, then waits on ECS's own
 * rollout state.
 */
export interface IEcsNativeRollbackServerGroupJob extends IServerGroupJob {
  reason?: string;
  serverGroupName: string;
  taskDefinition: string;
  forceNewDeployment: boolean;
}

export interface IEcsNativeRollbackServerGroupErrors {
  taskDefinition?: string;
}

interface IEcsNativeRollbackServerGroupModalState {
  initialValues: IEcsNativeRollbackServerGroupValues;
  revisions: IEcsTaskDefinitionRevision[];
  taskMonitor: TaskMonitor;
  verified: boolean;
}

export function validateEcsNativeRollbackValues(
  values: IEcsNativeRollbackServerGroupValues,
  eligibleTaskDefinitions?: string[],
): IEcsNativeRollbackServerGroupErrors {
  const errors: IEcsNativeRollbackServerGroupErrors = {};
  if (!values.taskDefinition) {
    errors.taskDefinition = 'Select a task definition to roll back to';
  } else if (eligibleTaskDefinitions && !eligibleTaskDefinitions.includes(values.taskDefinition)) {
    errors.taskDefinition = 'Select an eligible task definition';
  }
  return errors;
}

/** Options exclude the currently-running revision — you cannot "roll back" to what is deployed. */
function rollbackTargets(revisions: IEcsTaskDefinitionRevision[]): IEcsTaskDefinitionRevision[] {
  return revisions.filter((revision) => !revision.current);
}

function optionLabel(revision: IEcsTaskDefinitionRevision): string {
  const images = revision.containerImages?.length ? ` (${revision.containerImages.join(', ')})` : '';
  return `${revision.family}:${revision.revision}${images}`;
}

export class EcsNativeRollbackServerGroupModal extends React.Component<
  IEcsNativeRollbackServerGroupModalProps,
  IEcsNativeRollbackServerGroupModalState
> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  public static defaultProps: Partial<IEcsNativeRollbackServerGroupModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(
    props: IEcsNativeRollbackServerGroupModalProps,
    runtimeServices: DeckRuntimeServices,
  ): Promise<IEcsNativeRollbackServerGroupJob> {
    return ReactModal.show(EcsNativeRollbackServerGroupModal, props, undefined, runtimeServices);
  }

  public constructor(props: IEcsNativeRollbackServerGroupModalProps) {
    super(props);
    const { application, serverGroup } = props;
    // clouddriver attaches the family's revisions to the details payload for native services, so
    // they're already present on the server group by the time the actions menu can open this modal.
    const revisions = ((serverGroup as any).taskDefinitionRevisions as IEcsTaskDefinitionRevision[]) || [];
    this.state = {
      initialValues: {},
      revisions,
      taskMonitor: new TaskMonitor({
        application,
        title: `Rollback ${serverGroup.name}`,
        onDismiss: () => this.props.dismissModal(),
        onTaskComplete: () => application.serverGroups.refresh(),
      }),
      verified: false,
    };
  }

  private close = (): void => this.props.dismissModal();

  private submit = (values: IEcsNativeRollbackServerGroupValues): void => {
    const eligible = rollbackTargets(this.state.revisions).map(({ taskDefinitionArn }) => taskDefinitionArn);
    if (!this.state.verified || Object.keys(validateEcsNativeRollbackValues(values, eligible)).length > 0) {
      return;
    }

    const { application, serverGroup } = this.props;
    const command: IEcsNativeRollbackServerGroupJob = {
      // Orca registers the stage type from the class name (EcsNativeRollbackServerGroupStage ->
      // ecsNativeRollbackServerGroup), not from its PIPELINE_CONFIG_TYPE constant, so this must be
      // the camelCase class-derived name.
      type: 'ecsNativeRollbackServerGroup',
      cloudProvider: 'ecs-native',
      credentials: serverGroup.account,
      region: serverGroup.region,
      serverGroupName: serverGroup.name,
      taskDefinition: values.taskDefinition,
      forceNewDeployment: true,
      reason: values.reason,
    };

    this.state.taskMonitor.submit(() =>
      TaskExecutor.executeTask({
        job: [command],
        application,
        description: `Rollback ${serverGroup.name} to ${values.taskDefinition}`,
      }),
    );
  };

  public render(): JSX.Element {
    const { serverGroup } = this.props;
    const targets = rollbackTargets(this.state.revisions);

    return (
      <>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <SpinFormik<IEcsNativeRollbackServerGroupValues>
          initialValues={this.state.initialValues}
          onSubmit={this.submit}
          validate={(values) =>
            validateEcsNativeRollbackValues(
              values,
              targets.map(({ taskDefinitionArn }) => taskDefinitionArn),
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
                    <div className="col-sm-12">
                      Roll the service back to an earlier task-definition revision. ECS performs a normal in-place
                      deployment to that revision (subject to the deployment circuit breaker), not an instantaneous
                      swap.
                    </div>
                  </div>
                  {targets.length === 0 && (
                    <div className="form-group">
                      <div className="col-sm-12">
                        <ValidationMessage
                          message="No earlier task-definition revisions are available to roll back to."
                          type="warning"
                        />
                      </div>
                    </div>
                  )}
                  {targets.length > 0 && (
                    <div className="form-group">
                      <div className="col-sm-3 sm-label-right">Roll back to</div>
                      <div className="col-sm-7">
                        <FormikFormField
                          name="taskDefinition"
                          fastField={false}
                          input={(props) => (
                            <ReactSelectInput
                              {...props}
                              clearable={false}
                              options={targets.map((revision) => ({
                                label: optionLabel(revision),
                                value: revision.taskDefinitionArn,
                              }))}
                            />
                          )}
                          required={true}
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
                  disabled={!this.state.verified || !formik.isValid || targets.length === 0}
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
