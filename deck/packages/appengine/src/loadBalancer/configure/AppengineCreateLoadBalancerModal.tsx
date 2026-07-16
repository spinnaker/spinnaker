import { cloneDeep } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';

import type { ILoadBalancerModalProps } from '@spinnaker/core';
import { LoadBalancerWriter, ModalClose, noop, ReactModal, TaskMonitor, TaskMonitorWrapper } from '@spinnaker/core';

import type { IAppengineLoadBalancer } from '../../domain';
import type { IAppengineAllocationDescription } from '../transformer';
import { AppengineLoadBalancerUpsertDescription } from '../transformer';

const TARGET_OPTIONS = [
  { label: 'Current Server Group', value: 'current_asg_dynamic' },
  { label: 'Previous Server Group', value: 'ancestor_asg_dynamic' },
  { label: 'Oldest Server Group', value: 'oldest_asg_dynamic' },
];

export function updateAllocationLocatorType(
  allocation: IAppengineAllocationDescription,
  locatorType: IAppengineAllocationDescription['locatorType'],
): IAppengineAllocationDescription {
  const updated = { ...allocation, locatorType };
  if (locatorType === 'targetCoordinate') {
    delete updated.serverGroupName;
    updated.target = updated.target || TARGET_OPTIONS[0].value;
  } else {
    delete updated.cluster;
    delete updated.target;
  }
  return updated;
}

interface IAppengineCreateLoadBalancerModalState {
  taskMonitor: TaskMonitor;
}

export class AppengineCreateLoadBalancerModal extends React.Component<
  ILoadBalancerModalProps,
  IAppengineCreateLoadBalancerModalState
> {
  public static defaultProps: Partial<ILoadBalancerModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: ILoadBalancerModalProps & { isNew?: boolean }): Promise<IAppengineLoadBalancer> {
    return ReactModal.show(AppengineCreateLoadBalancerModal, props, { dialogClassName: 'modal-lg' });
  }

  public state: IAppengineCreateLoadBalancerModalState = {
    taskMonitor: new TaskMonitor({
      application: this.props.app,
      title: 'Updating your load balancer',
      modalInstance: TaskMonitor.modalInstanceEmulation(() => this.props.dismissModal()),
      onTaskComplete: () => this.props.app.getDataSource('loadBalancers').refresh(),
    }),
  };

  private command: AppengineLoadBalancerUpsertDescription = this.buildInitialCommand();

  private buildInitialCommand(): AppengineLoadBalancerUpsertDescription {
    const loadBalancer = (this.props.loadBalancer || {}) as IAppengineLoadBalancer;
    const command = new AppengineLoadBalancerUpsertDescription({
      ...loadBalancer,
      account: loadBalancer.account || loadBalancer.credentials,
      credentials: loadBalancer.credentials || loadBalancer.account,
      name: loadBalancer.name,
      region: loadBalancer.region,
      serverGroups: loadBalancer.serverGroups || [],
      cloudProvider: 'appengine',
      migrateTraffic: loadBalancer.migrateTraffic || false,
    } as IAppengineLoadBalancer);

    command.splitDescription = loadBalancer.splitDescription ||
      (loadBalancer.split &&
        AppengineLoadBalancerUpsertDescription.convertTrafficSplitToTrafficSplitDescription(loadBalancer.split)) || {
        shardBy: 'UNSPECIFIED',
        allocationDescriptions: (loadBalancer.serverGroups || []).slice(0, 1).map((serverGroup) => ({
          allocation: 100,
          locatorType: 'fromExisting',
          serverGroupName: serverGroup.name,
        })),
      };
    command.mapAllocationsToPercentages();

    return command;
  }

  private update = (field: keyof IAppengineLoadBalancer, value: any) => {
    (this.command as any)[field] = value;
    this.forceUpdate();
  };

  private updateSplit = (field: string, value: any) => {
    (this.command.splitDescription as any)[field] = value;
    this.forceUpdate();
  };

  private updateAllocation = (index: number, field: string, value: any) => {
    (this.command.splitDescription.allocationDescriptions[index] as any)[field] = value;
    this.forceUpdate();
  };

  private updateAllocationLocatorType = (
    index: number,
    locatorType: IAppengineAllocationDescription['locatorType'],
  ) => {
    this.command.splitDescription.allocationDescriptions[index] = updateAllocationLocatorType(
      this.command.splitDescription.allocationDescriptions[index],
      locatorType,
    );
    this.forceUpdate();
  };

  private addAllocation = () => {
    this.command.splitDescription.allocationDescriptions.push({
      allocation: 0,
      locatorType: this.props.forPipelineConfig ? 'text' : 'fromExisting',
      serverGroupName: '',
    });
    if (this.command.splitDescription.allocationDescriptions.length > 1 && !this.command.splitDescription.shardBy) {
      this.command.splitDescription.shardBy = 'IP';
    }
    this.forceUpdate();
  };

  private removeAllocation = (index: number) => {
    this.command.splitDescription.allocationDescriptions.splice(index, 1);
    this.forceUpdate();
  };

  private allocationTotal = () => {
    return this.command.splitDescription.allocationDescriptions.reduce((sum, description) => {
      return sum + Number(description.allocation || 0);
    }, 0);
  };

  private submit = () => {
    const description = cloneDeep(this.command);
    description.mapAllocationsToDecimals();
    delete description.serverGroups;

    if (this.props.forPipelineConfig) {
      this.props.closeModal(description as any);
      return;
    }

    this.state.taskMonitor.submit(() => LoadBalancerWriter.upsertLoadBalancer(description, this.props.app, 'Update'));
  };

  private renderAllocationTarget(allocation: IAppengineAllocationDescription, index: number) {
    if (!this.props.forPipelineConfig) {
      return (
        <input
          className="form-control input-sm"
          onChange={(event) => this.updateAllocation(index, 'serverGroupName', event.target.value)}
          value={allocation.serverGroupName || ''}
        />
      );
    }

    return (
      <div>
        <div className="radio">
          <label>
            <input
              checked={allocation.locatorType === 'text'}
              onChange={() => this.updateAllocationLocatorType(index, 'text')}
              type="radio"
            />{' '}
            Text input
          </label>
        </div>
        <div className="radio">
          <label>
            <input
              checked={allocation.locatorType === 'targetCoordinate'}
              onChange={() => this.updateAllocationLocatorType(index, 'targetCoordinate')}
              type="radio"
            />{' '}
            Target coordinate
          </label>
        </div>
        <div className="radio">
          <label>
            <input
              checked={allocation.locatorType === 'fromExisting'}
              onChange={() => this.updateAllocationLocatorType(index, 'fromExisting')}
              type="radio"
            />{' '}
            Existing server group
          </label>
        </div>
        {allocation.locatorType === 'targetCoordinate' ? (
          <div>
            <input
              className="form-control input-sm"
              onChange={(event) => this.updateAllocation(index, 'cluster', event.target.value)}
              placeholder="Cluster"
              value={allocation.cluster || ''}
            />
            <select
              className="form-control input-sm"
              onChange={(event) => this.updateAllocation(index, 'target', event.target.value)}
              value={allocation.target || TARGET_OPTIONS[0].value}
            >
              {TARGET_OPTIONS.map((target) => (
                <option key={target.value} value={target.value}>
                  {target.label}
                </option>
              ))}
            </select>
          </div>
        ) : (
          <input
            className="form-control input-sm"
            onChange={(event) => this.updateAllocation(index, 'serverGroupName', event.target.value)}
            placeholder={allocation.locatorType === 'text' ? 'Server group expression or name' : 'Server group'}
            value={allocation.serverGroupName || ''}
          />
        )}
      </div>
    );
  }

  public render() {
    const { dismissModal, isNew } = this.props as ILoadBalancerModalProps & { isNew?: boolean };

    return (
      <div>
        <ModalClose dismiss={dismissModal} />
        <Modal.Header>
          <Modal.Title>{isNew ? 'Create' : 'Edit'} App Engine Load Balancer</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <div className="form-horizontal">
            <label>Name</label>
            <input
              className="form-control input-sm"
              onChange={(event) => this.update('name', event.target.value)}
              value={this.command.name || ''}
            />
            <label>Account</label>
            <input
              className="form-control input-sm"
              onChange={(event) => {
                this.update('credentials', event.target.value);
                this.update('account', event.target.value);
              }}
              value={this.command.credentials || this.command.account || ''}
            />
            <label>Region</label>
            <input
              className="form-control input-sm"
              onChange={(event) => this.update('region', event.target.value)}
              value={this.command.region || ''}
            />
            <div className="checkbox">
              <label>
                <input
                  checked={!!this.command.migrateTraffic}
                  onChange={(event) => this.update('migrateTraffic', event.target.checked)}
                  type="checkbox"
                />{' '}
                Migrate traffic
              </label>
            </div>
            {(this.command.splitDescription.allocationDescriptions.length > 1 || this.command.migrateTraffic) && (
              <div>
                <label>Shard By</label>
                <select
                  className="form-control input-sm"
                  onChange={(event) => this.updateSplit('shardBy', event.target.value)}
                  value={this.command.splitDescription.shardBy || 'UNSPECIFIED'}
                >
                  <option value="UNSPECIFIED">Unspecified</option>
                  <option value="IP">IP</option>
                  <option value="COOKIE">Cookie</option>
                </select>
              </div>
            )}
            <label>Allocations</label>
            <table className="table table-condensed">
              <thead>
                <tr>
                  <th>Server Group</th>
                  <th>Allocation %</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {this.command.splitDescription.allocationDescriptions.map((allocation, index) => (
                  <tr key={index}>
                    <td>{this.renderAllocationTarget(allocation, index)}</td>
                    <td>
                      <input
                        className="form-control input-sm"
                        min={0}
                        onChange={(event) => this.updateAllocation(index, 'allocation', Number(event.target.value))}
                        type="number"
                        value={allocation.allocation || 0}
                      />
                    </td>
                    <td>
                      <button
                        className="btn btn-sm btn-link"
                        onClick={() => this.removeAllocation(index)}
                        type="button"
                      >
                        <span className="glyphicon glyphicon-trash" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <button className="btn btn-block btn-sm add-new" onClick={this.addAllocation} type="button">
              <span className="glyphicon glyphicon-plus-sign" /> Add allocation
            </button>
            {this.allocationTotal() !== 100 && (
              <div className="alert alert-warning">Traffic allocations must add up to 100%.</div>
            )}
          </div>
        </Modal.Body>
        <TaskMonitorWrapper monitor={this.state.taskMonitor} />
        <Modal.Footer>
          <button className="btn btn-default" onClick={() => dismissModal()} type="button">
            Cancel
          </button>
          <button
            className="btn btn-primary"
            disabled={this.allocationTotal() !== 100}
            onClick={this.submit}
            type="button"
          >
            {isNew ? 'Create' : 'Done'}
          </button>
        </Modal.Footer>
      </div>
    );
  }
}
