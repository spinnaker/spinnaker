import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { Application, ILoadBalancer, ReactInjector, Tooltip } from '@spinnaker/core';

export interface ICreateLoadBalancerButtonProps {
  app: Application;
}

export interface ICreateLoadBalancerButtonState {
  provider: any;
  showModal: boolean;
}

@BindAll()
export class CreateLoadBalancerButton extends React.Component<ICreateLoadBalancerButtonProps, ICreateLoadBalancerButtonState> {

  constructor(props: ICreateLoadBalancerButtonProps) {
    super(props);
    this.state = {
      provider: null,
      showModal: false,
    };
  }

  public componentDidMount(): void {
    const { providerSelectionService, cloudProviderRegistry, versionSelectionService } = ReactInjector;
    const { app } = this.props;
    providerSelectionService.selectProvider(app, 'loadBalancer').then((selectedProvider) => {
      versionSelectionService.selectVersion(selectedProvider).then((selectedVersion) => {
        this.setState({ provider: cloudProviderRegistry.getValue(selectedProvider, 'loadBalancer', selectedVersion) });
      });
    });
  }

  private createLoadBalancer(): void {
    const { provider } = this.state;
    if (!provider) { return; }

    if (provider.CreateLoadBalancerModal) {
      // react
      this.setState({ showModal: true });
    } else {
      // angular
      ReactInjector.modalService.open({
        templateUrl: provider.createLoadBalancerTemplateUrl,
        controller: `${provider.createLoadBalancerController} as ctrl`,
        size: 'lg',
        resolve: {
          application: () => this.props.app,
          loadBalancer: (): ILoadBalancer => null,
          isNew: () => true,
          forPipelineConfig: () => false
        }
      }).result.catch(() => {});
    }
  }

  private showModal(show: boolean): void {
    this.setState({ showModal: show });
  }

  public render() {
    const { app } = this.props;
    const { provider, showModal } = this.state;

    const CreateLoadBalancerModal = provider ? provider.CreateLoadBalancerModal : null;

    return (
      <div>
        <button className="btn btn-sm btn-default" onClick={this.createLoadBalancer}>
          <span className="glyphicon glyphicon-plus-sign visible-lg-inline"/>
          <Tooltip value="Create Load Balancer">
            <span className="glyphicon glyphicon-plus-sign visible-md-inline visible-sm-inline"/>
          </Tooltip>
          <span className="visible-lg-inline"> Create Load Balancer</span>
        </button>
        {CreateLoadBalancerModal && <CreateLoadBalancerModal app={app} forPipelineConfig={false} loadBalancer={null} show={showModal} showCallback={this.showModal} />}
      </div>
    );
  }
}
