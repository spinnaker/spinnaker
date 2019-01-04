import * as React from 'react';
import { UISref } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react-hybrid';

import { Application, ConfirmationModalService, LoadBalancerWriter, Spinner } from '@spinnaker/core';
import { CloudFoundryLoadBalancerActions } from 'cloudfoundry/loadBalancer/details/CloudFoundryLoadBalancerActions';
import { CloudFoundryLoadBalancerDetailsSection } from 'cloudfoundry/loadBalancer/details/sections';
import { ICloudFoundryLoadBalancer } from 'cloudfoundry/domain';
import { CloudFoundryLoadBalancerStatusSection } from 'cloudfoundry/loadBalancer/details/sections/CloudFoundryLoadBalancerStatusSection';
import { CloudFoundryLoadBalancerLinksSection } from 'cloudfoundry/loadBalancer/details/sections/CloudFoundryLoadBalancerLinksSection';

export interface ICloudFoundryLoadBalancerDetailsProps {
  application: Application;
  confirmationModalService: ConfirmationModalService;
  loadBalancer: ICloudFoundryLoadBalancer;
  loadBalancerNotFound: string;
  loadBalancerWriter: LoadBalancerWriter;
  loading: boolean;
}

@UIRouterContext
export class CloudFoundryLoadBalancerDetails extends React.Component<ICloudFoundryLoadBalancerDetailsProps> {
  constructor(props: ICloudFoundryLoadBalancerDetailsProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { application, confirmationModalService, loadBalancer, loadBalancerNotFound, loading } = this.props;

    const CloseButton = (
      <div className="close-button">
        <UISref to="^">
          <span className="glyphicon glyphicon-remove" />
        </UISref>
      </div>
    );
    const loadingHeader = () => (
      <div className="header">
        {CloseButton}
        <div className="horizontal center middle">
          <Spinner size="small" />
        </div>
      </div>
    );
    const notFoundHeader = () => (
      <div className="header">
        {CloseButton}
        <div className="header-text horizontal middle">
          <h3 className="horizontal middle space-between flex-1">{loadBalancerNotFound}</h3>
        </div>
      </div>
    );
    const loadBalancerHeader = () => (
      <div className="header">
        {CloseButton}
        <div className="header-text horizontal middle">
          <i className="fa icon-sitemap" />
          <h3 className="horizontal middle space-between flex-1">{loadBalancer.name}</h3>
        </div>
        <CloudFoundryLoadBalancerActions
          application={application}
          confirmationModalService={confirmationModalService}
          loadBalancer={loadBalancer}
        />
      </div>
    );
    const notFoundContent = () => (
      <div className="content">
        <div className="content-section">
          <div className="content-body text-center">
            <h3>Instance not found.</h3>
          </div>
        </div>
      </div>
    );
    const loadBalancerContent = () => (
      <div className="content">
        <CloudFoundryLoadBalancerDetailsSection loadBalancer={loadBalancer} />
        <CloudFoundryLoadBalancerStatusSection loadBalancer={loadBalancer} />
        <CloudFoundryLoadBalancerLinksSection loadBalancer={loadBalancer} />
      </div>
    );
    return (
      <div className="details-panel">
        {loading && loadingHeader()}
        {!loading && loadBalancer && loadBalancerHeader()}
        {!loading && loadBalancer && loadBalancerContent()}
        {!loading && !loadBalancer && notFoundHeader()}
        {!loading && !loadBalancer && notFoundContent()}
      </div>
    );
  }
}
