import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { Application } from 'core/application';
import { CloudProviderRegistry } from 'core/cloudProvider';
import { ILoadBalancer } from 'core/domain';
import { ILoadBalancerUpsertCommand } from 'core/loadBalancer';
import { ModalInjector, ReactInjector } from 'core/reactShims';
import { Tooltip } from 'core/presentation';

export interface ILoadBalancerModalProps {
  app: Application;
  forPipelineConfig?: boolean;
  loadBalancer: ILoadBalancer;
  show: boolean;
  showCallback: (show: boolean) => void;
  onComplete?: (loadBalancerCommand: ILoadBalancerUpsertCommand) => void;
}

export interface ICreateLoadBalancerButtonProps {
  app: Application;
}

export interface ICreateLoadBalancerButtonState {
  Modal: React.ComponentType<any>;
  showModal: boolean;
}

@BindAll()
export class CreateLoadBalancerButton extends React.Component<
  ICreateLoadBalancerButtonProps,
  ICreateLoadBalancerButtonState
> {
  constructor(props: ICreateLoadBalancerButtonProps) {
    super(props);
    this.state = {
      Modal: null,
      showModal: false,
    };
  }

  private createLoadBalancer(): void {
    const { providerSelectionService, skinSelectionService } = ReactInjector;
    const { app } = this.props;
    providerSelectionService.selectProvider(app, 'loadBalancer').then(selectedProvider => {
      skinSelectionService.selectSkin(selectedProvider).then(selectedSkin => {
        const provider = CloudProviderRegistry.getValue(selectedProvider, 'loadBalancer', selectedSkin);

        if (provider.CreateLoadBalancerModal) {
          // react
          this.setState({ Modal: provider.CreateLoadBalancerModal, showModal: true });
        } else {
          // angular
          ModalInjector.modalService
            .open({
              templateUrl: provider.createLoadBalancerTemplateUrl,
              controller: `${provider.createLoadBalancerController} as ctrl`,
              size: 'lg',
              resolve: {
                application: () => this.props.app,
                loadBalancer: (): ILoadBalancer => null,
                isNew: () => true,
                forPipelineConfig: () => false,
              },
            })
            .result.catch(() => {});
        }
      });
    });
  }

  private showModal(show: boolean): void {
    this.setState({ showModal: show });
  }

  public render() {
    const { app } = this.props;
    const { Modal, showModal } = this.state;

    return (
      <div>
        <button className="btn btn-sm btn-default" onClick={this.createLoadBalancer}>
          <span className="glyphicon glyphicon-plus-sign visible-lg-inline" />
          <Tooltip value="Create Load Balancer">
            <span className="glyphicon glyphicon-plus-sign visible-md-inline visible-sm-inline" />
          </Tooltip>
          <span className="visible-lg-inline"> Create Load Balancer</span>
        </button>
        {Modal && (
          <Modal
            app={app}
            forPipelineConfig={false}
            loadBalancer={null}
            show={showModal}
            showCallback={this.showModal}
          />
        )}
      </div>
    );
  }
}
