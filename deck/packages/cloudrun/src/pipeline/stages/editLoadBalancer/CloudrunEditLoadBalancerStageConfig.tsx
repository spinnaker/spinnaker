import { cloneDeep } from 'lodash';
import React from 'react';

import type { ILoadBalancer, IStageConfigProps } from '@spinnaker/core';
import { AccountTag, ReactModal } from '@spinnaker/core';

import { getLoadBalancerCompositeKey } from './CloudrunEditLoadBalancerExecutionDetails';
import { LoadBalancerMessage } from '../../../common/LoadBalancerMessage';
import { CloudrunLoadBalancerModal } from '../../../loadBalancer/configure/wizard/CloudrunLoadBalancerModal';

export class CloudrunEditLoadBalancerStageConfig extends React.Component<IStageConfigProps> {
  public componentDidMount(): void {
    this.props.updateStage({
      loadBalancers: this.props.stage.loadBalancers || [],
      cloudProvider: 'cloudrun',
    });
  }

  private addLoadBalancer = (): void => {
    CloudrunLoadBalancerChoiceModal.show({ application: this.props.application })
      .then((newLoadBalancer) => {
        this.props.updateStage({ loadBalancers: [...(this.props.stage.loadBalancers || []), newLoadBalancer] });
      })
      .catch(() => undefined);
  };

  private editLoadBalancer = (index: number): void => {
    CloudrunLoadBalancerModal.show({
      app: this.props.application,
      loadBalancer: cloneDeep(this.props.stage.loadBalancers[index]),
      isNew: false,
      forPipelineConfig: true,
    })
      .then((updatedLoadBalancer) => {
        const loadBalancers = [...this.props.stage.loadBalancers];
        loadBalancers[index] = updatedLoadBalancer;
        this.props.updateStage({ loadBalancers });
      })
      .catch(() => undefined);
  };

  private removeLoadBalancer = (index: number): void => {
    const loadBalancers = [...this.props.stage.loadBalancers];
    loadBalancers.splice(index, 1);
    this.props.updateStage({ loadBalancers });
  };

  public render() {
    const { pipeline, stage } = this.props;
    if (pipeline.strategy) {
      return null;
    }
    return (
      <div className="well well-sm clearfix">
        <div className="row">
          <div className="col-md-12">
            <h4 className="text-left">Load Balancers</h4>
          </div>
        </div>
        <div className="row">
          <div className="col-md-12">
            <table className="table table-condensed">
              <thead>
                <tr>
                  <th>Account</th>
                  <th>Name</th>
                  <th>Region</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {(stage.loadBalancers || []).map((loadBalancer: ILoadBalancer, index: number) => (
                  <tr
                    key={`${loadBalancer.credentials || loadBalancer.account}:${loadBalancer.name}:${
                      loadBalancer.region
                    }`}
                  >
                    <td>
                      <AccountTag account={loadBalancer.credentials || loadBalancer.account} />
                    </td>
                    <td>{loadBalancer.name}</td>
                    <td>{loadBalancer.region}</td>
                    <td className="condensed-actions">
                      <button
                        type="button"
                        className="btn btn-sm btn-link"
                        onClick={() => this.editLoadBalancer(index)}
                      >
                        <span className="glyphicon glyphicon-edit" title="Edit" />
                      </button>
                      <button
                        type="button"
                        className="btn btn-sm btn-link pad-left"
                        onClick={() => this.removeLoadBalancer(index)}
                      >
                        <span className="glyphicon glyphicon-trash" title="Remove" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={8}>
                    <button type="button" className="btn btn-block btn-sm add-new" onClick={this.addLoadBalancer}>
                      <span className="glyphicon glyphicon-plus-sign" /> Add load balancer
                    </button>
                  </td>
                </tr>
              </tfoot>
            </table>
          </div>
        </div>
      </div>
    );
  }
}

interface ICloudrunLoadBalancerChoiceModalProps {
  application: IStageConfigProps['application'];
  closeModal?: (loadBalancer: ILoadBalancer) => void;
  dismissModal?: () => void;
}

interface ICloudrunLoadBalancerChoiceModalState {
  loading: boolean;
  loadBalancers: ILoadBalancer[];
  selectedLoadBalancer?: ILoadBalancer;
}

export class CloudrunLoadBalancerChoiceModal extends React.Component<
  ICloudrunLoadBalancerChoiceModalProps,
  ICloudrunLoadBalancerChoiceModalState
> {
  public static show(props: ICloudrunLoadBalancerChoiceModalProps): Promise<ILoadBalancer> {
    return ReactModal.show(CloudrunLoadBalancerChoiceModal, props);
  }

  private mounted = false;

  public state: ICloudrunLoadBalancerChoiceModalState = { loading: true, loadBalancers: [] };

  public componentDidMount(): void {
    this.mounted = true;
    this.props.application.loadBalancers
      .ready()
      .then(() => {
        if (!this.mounted) {
          return;
        }
        const loadBalancers = (this.props.application.loadBalancers.data as ILoadBalancer[]).filter(
          (candidate) => candidate.cloudProvider === 'cloudrun',
        );
        this.setState({ loadBalancers, selectedLoadBalancer: loadBalancers[0], loading: false });
      })
      .catch(() => {
        if (this.mounted) {
          this.setState({ loading: false });
        }
      });
  }

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  private submit = (): void => {
    CloudrunLoadBalancerModal.show({
      app: this.props.application,
      loadBalancer: cloneDeep(this.state.selectedLoadBalancer),
      isNew: false,
      forPipelineConfig: true,
    })
      .then(this.props.closeModal)
      .catch(() => undefined);
  };

  public render() {
    const { dismissModal } = this.props;
    const { loading, loadBalancers, selectedLoadBalancer } = this.state;
    return (
      <div>
        <div className="modal-header">
          <button type="button" className="close" onClick={dismissModal}>
            <span>&times;</span>
          </button>
          <h4 className="modal-title">Select Load Balancer</h4>
        </div>
        <div className="modal-body">
          {loading && (
            <div style={{ height: 200 }} className="horizontal center middle">
              Loading...
            </div>
          )}
          {!loading && loadBalancers.length === 0 && <LoadBalancerMessage showCreateMessage={true} />}
          {!loading && loadBalancers.length > 0 && (
            <div className="form-horizontal">
              <div className="form-group">
                <div className="col-md-3 sm-label-right">
                  <b>Load Balancer</b>
                </div>
                <div className="col-md-7">
                  <select
                    className="form-control input-sm"
                    value={getLoadBalancerCompositeKey(selectedLoadBalancer)}
                    onChange={(event) =>
                      this.setState({
                        selectedLoadBalancer: loadBalancers.find(
                          (loadBalancer) => getLoadBalancerCompositeKey(loadBalancer) === event.target.value,
                        ),
                      })
                    }
                  >
                    {loadBalancers.map((loadBalancer) => (
                      <option
                        key={getLoadBalancerCompositeKey(loadBalancer)}
                        value={getLoadBalancerCompositeKey(loadBalancer)}
                      >
                        {getLoadBalancerOptionLabel(loadBalancer)}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button type="button" className="btn btn-default" onClick={dismissModal}>
            Cancel
          </button>
          {loadBalancers.length > 0 && (
            <button type="button" className="btn btn-primary" onClick={this.submit}>
              <span className="far fa-check-circle" /> Edit
            </button>
          )}
        </div>
      </div>
    );
  }
}

export function getLoadBalancerOptionLabel(loadBalancer: ILoadBalancer): string {
  return [loadBalancer.account, loadBalancer.name, loadBalancer.region].filter(Boolean).join(' ');
}
