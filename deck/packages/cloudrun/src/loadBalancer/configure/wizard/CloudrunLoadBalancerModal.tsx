import { cloneDeep, difference, uniq } from 'lodash';
import React from 'react';

import type { ILoadBalancerModalProps, IRouterInjectedProps } from '@spinnaker/core';
import { HelpField, LoadBalancerWriter, ReactModal, StageConstants, TaskMonitor, withRouter } from '@spinnaker/core';

import { LoadBalancerMessage } from '../../../common/LoadBalancerMessage';
import type { ICloudrunLoadBalancer } from '../../../common/domain';
import type { ICloudrunAllocationDescription, ICloudrunTrafficSplitDescription } from '../../loadBalancerTransformer';
import { CloudrunLoadBalancerTransformer, CloudrunLoadBalancerUpsertDescription } from '../../loadBalancerTransformer';

export interface ICloudrunLoadBalancerModalState {
  loading: boolean;
  loadBalancer?: CloudrunLoadBalancerUpsertDescription;
  taskMonitor: TaskMonitor;
}

export interface ICloudrunLoadBalancerModalProps extends ILoadBalancerModalProps {
  isNew?: boolean;
}

function allocationTotalIsValid(splitDescription: ICloudrunTrafficSplitDescription): boolean {
  return (
    splitDescription.allocationDescriptions.reduce((sum, description) => sum + Number(description.percent), 0) === 100
  );
}

export class CloudrunLoadBalancerModalComponent extends React.Component<
  ICloudrunLoadBalancerModalProps & IRouterInjectedProps,
  ICloudrunLoadBalancerModalState
> {
  public static show(props: ICloudrunLoadBalancerModalProps): Promise<CloudrunLoadBalancerUpsertDescription> {
    return ReactModal.show(CloudrunLoadBalancerModal, props, { dialogClassName: 'wizard-modal modal-lg' });
  }

  private transformer = new CloudrunLoadBalancerTransformer();
  private isUnmounted = false;

  constructor(props: ICloudrunLoadBalancerModalProps & IRouterInjectedProps) {
    super(props);
    this.state = {
      loading: !props.isNew,
      taskMonitor: new TaskMonitor({
        application: props.app,
        title: 'Updating your load balancer',
        modalInstance: TaskMonitor.modalInstanceEmulation(() => props.dismissModal()),
        onTaskComplete: this.onTaskComplete,
      }),
    };
  }

  public componentDidMount(): void {
    const { app, isNew, loadBalancer } = this.props;
    if (isNew) {
      return;
    }
    this.transformer
      .convertLoadBalancerForEditing(loadBalancer as ICloudrunLoadBalancer, app)
      .then((convertedLoadBalancer) => {
        if (this.isUnmounted) {
          return;
        }
        const command = this.transformer.convertLoadBalancerToUpsertDescription(
          convertedLoadBalancer as ICloudrunLoadBalancer,
        );
        if ((loadBalancer as ICloudrunLoadBalancer).split && !command.splitDescription) {
          command.splitDescription = CloudrunLoadBalancerUpsertDescription.convertTrafficSplitToTrafficSplitDescription(
            (loadBalancer as ICloudrunLoadBalancer).split,
          );
        } else {
          command.splitDescription = (loadBalancer as any).splitDescription;
        }
        command.mapAllocationsToPercentages();
        this.setState({ loadBalancer: command, loading: false });
      })
      .catch(() => {
        if (!this.isUnmounted) {
          this.props.dismissModal();
        }
      });
  }

  public componentWillUnmount(): void {
    this.isUnmounted = true;
  }

  private onTaskComplete = (): void => {
    this.props.app.loadBalancers.refresh();
    this.props.app.loadBalancers.onNextRefresh(null, this.onApplicationRefresh);
  };

  private onApplicationRefresh = (): void => {
    if (this.isUnmounted) {
      return;
    }
    const { loadBalancer } = this.state;
    this.props.dismissModal();
    const newStateParams = {
      name: loadBalancer.name,
      accountId: loadBalancer.credentials,
      region: loadBalancer.region,
      provider: 'cloudrun',
    };
    if (!this.props.stateService.includes('**.loadBalancerDetails')) {
      this.props.stateService.go('.loadBalancerDetails', newStateParams);
    } else {
      this.props.stateService.go('^.loadBalancerDetails', newStateParams);
    }
  };

  private submit = (): void => {
    const description = cloneDeep(this.state.loadBalancer);
    description.mapAllocationsToPercentages();
    delete description.serverGroups;

    if (this.props.forPipelineConfig) {
      this.props.closeModal(description);
    } else {
      this.state.taskMonitor.submit(() => LoadBalancerWriter.upsertLoadBalancer(description, this.props.app, 'Update'));
    }
  };

  private updateLoadBalancer = (loadBalancer: CloudrunLoadBalancerUpsertDescription): void => {
    this.setState({ loadBalancer });
  };

  public render() {
    const { dismissModal, forPipelineConfig, isNew, loadBalancer: sourceLoadBalancer } = this.props;
    const { loading, loadBalancer, taskMonitor } = this.state;
    const heading = isNew
      ? 'Create New Load Balancer'
      : `Edit ${[
          sourceLoadBalancer.name,
          sourceLoadBalancer.region,
          sourceLoadBalancer.account || sourceLoadBalancer.credentials,
        ].join(':')}`;
    const submitButtonLabel = forPipelineConfig ? 'Done' : 'Update';
    const canSubmit = loadBalancer?.splitDescription && allocationTotalIsValid(loadBalancer.splitDescription);

    return (
      <form name="form">
        <div className="modal-header">
          <button type="button" className="close" onClick={dismissModal}>
            <span>&times;</span>
          </button>
          <h4 className="modal-title">{heading}</h4>
        </div>
        <div className="modal-body">
          {loading && !isNew && (
            <div style={{ height: 200 }} className="horizontal center middle">
              Loading...
            </div>
          )}
          {isNew && <LoadBalancerMessage showCreateMessage={true} />}
          {!loading && !isNew && loadBalancer && (
            <CloudrunLoadBalancerBasicSettings
              loadBalancer={loadBalancer}
              forPipelineConfig={forPipelineConfig}
              onChange={this.updateLoadBalancer}
            />
          )}
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-default" onClick={dismissModal}>
            Cancel
          </button>
          {!isNew && canSubmit && (
            <button
              type="button"
              className="btn btn-primary"
              disabled={taskMonitor.submitting || loading}
              onClick={this.submit}
            >
              {submitButtonLabel}
            </button>
          )}
        </div>
      </form>
    );
  }
}

export const CloudrunLoadBalancerModal = Object.assign(withRouter(CloudrunLoadBalancerModalComponent), {
  show: CloudrunLoadBalancerModalComponent.show,
});

interface ICloudrunLoadBalancerBasicSettingsProps {
  loadBalancer: CloudrunLoadBalancerUpsertDescription;
  forPipelineConfig: boolean;
  onChange: (loadBalancer: CloudrunLoadBalancerUpsertDescription) => void;
}

class CloudrunLoadBalancerBasicSettings extends React.Component<ICloudrunLoadBalancerBasicSettingsProps> {
  private serverGroupsWithoutAllocation(loadBalancer = this.props.loadBalancer): string[] {
    const serverGroupsWithAllocation = loadBalancer.splitDescription.allocationDescriptions.map(
      (description) => description.revisionName,
    );
    const allServerGroups = loadBalancer.serverGroups.map((serverGroup) => serverGroup.name);
    return difference(allServerGroups, serverGroupsWithAllocation);
  }

  private changeLoadBalancer(mutator: (loadBalancer: CloudrunLoadBalancerUpsertDescription) => void): void {
    const loadBalancer = cloneDeep(this.props.loadBalancer);
    mutator(loadBalancer);
    this.props.onChange(loadBalancer);
  }

  private addAllocation = (): void => {
    this.changeLoadBalancer((loadBalancer) => {
      const remainingServerGroups = this.serverGroupsWithoutAllocation(loadBalancer);
      loadBalancer.splitDescription.allocationDescriptions.push({
        revisionName: remainingServerGroups[0] || '',
        percent: 0,
      });
    });
  };

  private removeAllocation = (index: number): void => {
    this.changeLoadBalancer((loadBalancer) => loadBalancer.splitDescription.allocationDescriptions.splice(index, 1));
  };

  private updateAllocation = (index: number, changes: Partial<ICloudrunAllocationDescription>): void => {
    this.changeLoadBalancer((loadBalancer) => {
      loadBalancer.splitDescription.allocationDescriptions[index] = {
        ...loadBalancer.splitDescription.allocationDescriptions[index],
        ...changes,
      };
    });
  };

  public render() {
    const { forPipelineConfig, loadBalancer } = this.props;
    const serverGroupOptions = this.serverGroupsWithoutAllocation();
    const showAddButton = forPipelineConfig || serverGroupOptions.length > 0;
    const allocationIsInvalid = !allocationTotalIsValid(loadBalancer.splitDescription);

    return (
      <div className="row">
        <div className="form-group">
          <div className="col-md-3 sm-label-right">
            Allocations <HelpField id="cloudrun.loadBalancer.allocations" />
          </div>
          <div className="col-md-9">
            {loadBalancer.splitDescription.allocationDescriptions.map((description, index) => (
              <CloudrunAllocationConfigurationRow
                key={index}
                allocationDescription={description}
                loadBalancer={loadBalancer}
                forPipelineConfig={forPipelineConfig}
                serverGroupOptions={serverGroupOptions}
                onChange={(changes) => this.updateAllocation(index, changes)}
                onRemove={() => this.removeAllocation(index)}
              />
            ))}
            {showAddButton && (
              <button type="button" className="add-new col-md-11" onClick={this.addAllocation}>
                <span className="glyphicon glyphicon-plus-sign" /> Add allocation
              </button>
            )}
          </div>
        </div>
        {allocationIsInvalid && (
          <div className="form-group">
            <div className="col-md-12 text-center">
              <p className="alert alert-warning">Allocations must sum to 100%.</p>
            </div>
          </div>
        )}
      </div>
    );
  }
}

interface ICloudrunAllocationConfigurationRowProps {
  allocationDescription: ICloudrunAllocationDescription;
  loadBalancer: CloudrunLoadBalancerUpsertDescription;
  forPipelineConfig: boolean;
  serverGroupOptions: string[];
  onChange: (changes: Partial<ICloudrunAllocationDescription>) => void;
  onRemove: () => void;
}

function CloudrunAllocationConfigurationRow({
  allocationDescription,
  forPipelineConfig,
  loadBalancer,
  onChange,
  onRemove,
  serverGroupOptions,
}: ICloudrunAllocationConfigurationRowProps) {
  const options = allocationDescription.revisionName
    ? uniq(serverGroupOptions.concat(allocationDescription.revisionName))
    : serverGroupOptions;
  const target = StageConstants.TARGET_LIST.find(
    (targetOption: any) => targetOption.val === allocationDescription.target,
  );
  const targetLabel = target ? target.label : '';

  return (
    <>
      <div className="form-group">
        <div className="row">
          <div className="col-md-7">
            {forPipelineConfig ? (
              <input
                value={
                  allocationDescription.cluster && allocationDescription.target
                    ? `${targetLabel} (${allocationDescription.cluster})`
                    : ''
                }
                type="text"
                className="form-control input-sm"
                readOnly={true}
              />
            ) : (
              <select
                value={allocationDescription.revisionName || ''}
                className="form-control input-sm"
                onChange={(event) => onChange({ revisionName: event.target.value })}
              >
                {options.map((serverGroup) => (
                  <option key={serverGroup} value={serverGroup}>
                    {serverGroup}
                  </option>
                ))}
              </select>
            )}
          </div>
          <div className="col-md-3">
            <div className="input-group input-group-sm">
              <input
                type="number"
                value={allocationDescription.percent}
                required={true}
                className="form-control input-sm"
                min={0}
                max={100}
                onChange={(event) => onChange({ percent: Number(event.target.value) })}
              />
              <span className="input-group-addon">%</span>
            </div>
          </div>
          <div className="col-md-2">
            <button type="button" className="btn btn-link sm-label" onClick={onRemove}>
              <span className="glyphicon glyphicon-trash" />
            </button>
          </div>
        </div>
      </div>
      {forPipelineConfig && (
        <div className="form-group">
          <div className="well col-md-11" style={{ paddingTop: 5, paddingBottom: 10 }}>
            <div className="row">
              <div className="form-group">
                <div className="col-md-3 sm-label-right">Cluster</div>
                <div className="col-md-7" style={{ marginBottom: 10 }}>
                  <input
                    value={allocationDescription.cluster || loadBalancer.name}
                    type="text"
                    className="form-control input-sm"
                    readOnly={true}
                  />
                </div>
                <div className="col-md-3 sm-label-right">Target</div>
                <div className="col-md-7">
                  <select
                    value={allocationDescription.target || ''}
                    className="form-control input-sm"
                    onChange={(event) => onChange({ target: event.target.value, cluster: loadBalancer.name })}
                  >
                    <option value="">Select...</option>
                    {StageConstants.TARGET_LIST.map((targetOption: any) => (
                      <option key={targetOption.val} value={targetOption.val}>
                        {targetOption.label}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
