import * as React from 'react';

import { Overridable, IOverridableProps } from 'core/overrideRegistry';
import { ReactInjector } from 'core/reactShims';
import { Application } from 'core/application';
import { Spinner } from 'core/widgets';

export interface IInstanceDetailsProps extends IOverridableProps {
  $stateParams: {
    provider: string;
    instanceId: string;
  },
  app: Application;
}

export class InstanceDetails extends React.Component<IInstanceDetailsProps, { accountId: string }> {
  public constructor(props: any) {
    super(props);

    const { app, $stateParams: { provider, instanceId } } = this.props;
    ReactInjector.versionedCloudProviderService.getAccountForInstance(provider, instanceId, app).then(accountId => {
      this.setState({ accountId })
    });
    this.state = { accountId: null };
  }

  public render() {

    const { accountId } = this.state;
    if (accountId) {
      return <InstanceDetailsCmp {...this.props} accountId={accountId} />;
    }
    return <Spinner/>;
  }
}

@Overridable('instance.details')
export class InstanceDetailsCmp extends React.Component<IInstanceDetailsProps> {
  public render() {
    return (
      <h3>Instance Details</h3>
    );
  }
}

