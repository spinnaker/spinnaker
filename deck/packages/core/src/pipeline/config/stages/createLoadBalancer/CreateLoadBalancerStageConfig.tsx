import { cloneDeep } from 'lodash';
import React from 'react';

import { AccountTag } from '../../../../account/AccountTag';
import { CloudProviderRegistry, ProviderSelectionService } from '../../../../cloudProvider';
import type { IStageConfigProps } from '../common';
import type { ILoadBalancer } from '../../../../domain';
import { openLoadBalancerModal } from './openLoadBalancerModal';
import { ModalInjector } from '../../../../reactShims';

export interface ICreateLoadBalancerStageConfigState {
  loadBalancers: ILoadBalancer[];
}

export class CreateLoadBalancerStageConfig extends React.Component<
  IStageConfigProps,
  ICreateLoadBalancerStageConfigState
> {
  constructor(props: IStageConfigProps) {
    super(props);
    this.ensureStageDefaults(props);
    this.state = { loadBalancers: props.stage.loadBalancers };
  }

  public componentDidUpdate(prevProps: IStageConfigProps): void {
    if (prevProps.stage !== this.props.stage) {
      this.ensureStageDefaults(this.props);
      this.setState({ loadBalancers: this.props.stage.loadBalancers });
    }
  }

  private ensureStageDefaults(props: IStageConfigProps): void {
    props.stage.loadBalancers = props.stage.loadBalancers || [];
  }

  private updateLoadBalancers(loadBalancers: ILoadBalancer[]): void {
    this.props.stage.loadBalancers = loadBalancers;
    this.setState({ loadBalancers });
    this.props.stageFieldUpdated();
  }

  private openProviderModal(loadBalancer: ILoadBalancer, isNew: boolean): PromiseLike<ILoadBalancer | ILoadBalancer[]> {
    return ProviderSelectionService.selectProvider(this.props.application, 'loadBalancer').then((selectedProvider) => {
      const config = CloudProviderRegistry.getValue(selectedProvider, 'loadBalancer');
      return openLoadBalancerModal(config, ModalInjector.modalService, {
        application: this.props.application,
        loadBalancer,
        isNew,
        forPipelineConfig: true,
      });
    });
  }

  private addLoadBalancer = (): void => {
    this.openProviderModal(null, true)
      .then((newLoadBalancer) => {
        const newLoadBalancers = Array.isArray(newLoadBalancer) ? newLoadBalancer : [newLoadBalancer];
        this.updateLoadBalancers([...this.state.loadBalancers, ...newLoadBalancers]);
      })
      .catch(() => {});
  };

  private editLoadBalancer = (loadBalancer: ILoadBalancer, index: number): void => {
    this.openProviderModal(cloneDeep(loadBalancer), false)
      .then((updatedLoadBalancer) => {
        const updatedLoadBalancers = Array.isArray(updatedLoadBalancer) ? updatedLoadBalancer : [updatedLoadBalancer];
        this.updateLoadBalancers([
          ...this.state.loadBalancers.slice(0, index),
          ...updatedLoadBalancers,
          ...this.state.loadBalancers.slice(index + 1),
        ]);
      })
      .catch(() => {});
  };

  private copyLoadBalancer = (index: number): void => {
    this.updateLoadBalancers(this.state.loadBalancers.concat(cloneDeep(this.state.loadBalancers[index])));
  };

  private removeLoadBalancer = (index: number): void => {
    const loadBalancers = this.state.loadBalancers.slice(0);
    loadBalancers.splice(index, 1);
    this.updateLoadBalancers(loadBalancers);
  };

  public render(): React.ReactNode {
    if (this.props.pipeline.strategy) {
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
                  <th>Subnet</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {this.state.loadBalancers.map((loadBalancer, index) => (
                  <tr key={`${loadBalancer.credentials}-${loadBalancer.region}-${loadBalancer.name}-${index}`}>
                    <td>
                      <AccountTag account={loadBalancer.credentials} />
                    </td>
                    <td>{loadBalancer.name}</td>
                    <td>{loadBalancer.region}</td>
                    <td>{loadBalancer.subnetType || '[none]'}</td>
                    <td>
                      <button
                        className="btn btn-xs btn-link"
                        type="button"
                        onClick={() => this.editLoadBalancer(loadBalancer, index)}
                      >
                        Edit
                      </button>
                      <button
                        className="btn btn-xs btn-link pad-left"
                        type="button"
                        onClick={() => this.removeLoadBalancer(index)}
                      >
                        Remove
                      </button>
                      <button
                        className="btn btn-xs btn-link pad-left"
                        type="button"
                        onClick={() => this.copyLoadBalancer(index)}
                      >
                        Duplicate
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={5}>
                    <button className="btn btn-block btn-sm add-new" type="button" onClick={this.addLoadBalancer}>
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
