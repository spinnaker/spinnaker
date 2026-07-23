import React from 'react';

import type { IAccountDetails } from '../account';
import type { Application } from '../application';
import { DeckRuntimeContext } from '../bootstrap/DeckRuntimeContext';
import type { ICloudProviderConfig } from '../cloudProvider';
import { CloudProviderRegistry, ProviderSelectionService } from '../cloudProvider';
import type { ILoadBalancer } from '../domain';
import type { ILoadBalancerUpsertCommand } from './loadBalancer.write.service';
import type { IModalComponentProps } from '../presentation';
import { Tooltip } from '../presentation';

export interface ILoadBalancerModalProps extends IModalComponentProps {
  className?: string;
  dialogClassName?: string;
  app: Application;
  forPipelineConfig?: boolean;
  loadBalancer: ILoadBalancer;
  command?: ILoadBalancerUpsertCommand; // optional, when ejecting from a wizard
  closeModal?(loadBalancerCommand: ILoadBalancerUpsertCommand): void; // provided by ReactModal
  dismissModal?(rejectReason?: any): void; // provided by ReactModal
}

export interface ICreateLoadBalancerButtonProps {
  app: Application;
}

export class CreateLoadBalancerButton extends React.Component<ICreateLoadBalancerButtonProps, { isDisabled: boolean }> {
  public static contextType = DeckRuntimeContext;
  public declare context: React.ContextType<typeof DeckRuntimeContext>;

  constructor(props: ICreateLoadBalancerButtonProps) {
    super(props);

    const { app } = this.props;
    this.state = { isDisabled: true };
    ProviderSelectionService.isDisabled(app).then((val) => {
      this.setState({
        isDisabled: val,
      });
    });
  }

  private createLoadBalancerProviderFilterFn = (
    _app: Application,
    _acc: IAccountDetails,
    provider: ICloudProviderConfig,
  ): boolean => {
    const lbConfig = provider.loadBalancer;
    return Boolean(lbConfig && lbConfig.CreateLoadBalancerModal);
  };

  private createLoadBalancer = (): void => {
    const { app } = this.props;
    ProviderSelectionService.selectProvider(app, 'loadBalancer', this.createLoadBalancerProviderFilterFn).then(
      (selectedProvider) => {
        const provider = CloudProviderRegistry.getValue(selectedProvider, 'loadBalancer');

        provider.CreateLoadBalancerModal.show(
          {
            app: app,
            application: app,
            forPipelineConfig: false,
            loadBalancer: null,
            isNew: true,
          },
          this.context.services,
        );
      },
      () => {},
    );
  };

  public render() {
    if (!this.state.isDisabled) {
      return (
        <div>
          <button className="btn btn-sm btn-default" onClick={this.createLoadBalancer}>
            <span className="glyphicon glyphicon-plus-sign visible-lg-inline" />
            <Tooltip value="Create Load Balancer">
              <span className="glyphicon glyphicon-plus-sign visible-md-inline visible-sm-inline" />
            </Tooltip>
            <span className="visible-lg-inline"> Create Load Balancer</span>
          </button>
        </div>
      );
    } else {
      return <div></div>;
    }
  }
}
