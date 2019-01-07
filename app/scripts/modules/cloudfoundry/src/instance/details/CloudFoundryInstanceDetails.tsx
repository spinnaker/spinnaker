import * as React from 'react';
import { UISref } from '@uirouter/react';
import { UIRouterContext } from '@uirouter/react-hybrid';

import { Application, ConfirmationModalService, InstanceWriter, Spinner } from '@spinnaker/core';

import { ICloudFoundryInstance } from 'cloudfoundry/domain';
import { CloudFoundryInstanceDetailsSection } from 'cloudfoundry/instance/details/sections';
import { CloudFoundryInstanceActions } from 'cloudfoundry/instance/details/CloudFoundryInstanceActions';

export interface ICloudFoundryInstanceDetailsProps {
  application: Application;
  confirmationModalService: ConfirmationModalService;
  instance: ICloudFoundryInstance;
  instanceIdNotFound: string;
  instanceWriter: InstanceWriter;
  loading: boolean;
}

@UIRouterContext
export class CloudFoundryInstanceDetails extends React.Component<ICloudFoundryInstanceDetailsProps> {
  constructor(props: ICloudFoundryInstanceDetailsProps) {
    super(props);
  }

  public render(): JSX.Element {
    const { application, confirmationModalService, instance, instanceIdNotFound, instanceWriter, loading } = this.props;
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
          <h3 className="horizontal middle space-between flex-1">{instanceIdNotFound}</h3>
        </div>
      </div>
    );
    const instanceHeader = () => (
      <div className="header">
        {CloseButton}
        <div className="header-text horizontal middle">
          <span className={'glyphicon glyphicon-hdd ' + instance.healthState} />
          <h3 className="horizontal middle space-between flex-1">{instance.name}</h3>
        </div>
        <CloudFoundryInstanceActions
          application={application}
          confirmationModalService={confirmationModalService}
          instance={instance}
          instanceWriter={instanceWriter}
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
    const instanceContent = () => (
      <div className="content">
        <CloudFoundryInstanceDetailsSection instance={instance} />
      </div>
    );
    return (
      <div className="details-panel">
        {loading && loadingHeader()}
        {!loading && instance && instanceHeader()}
        {!loading && instance && instanceContent()}
        {!loading && !instance && notFoundHeader()}
        {!loading && !instance && notFoundContent()}
      </div>
    );
  }
}
