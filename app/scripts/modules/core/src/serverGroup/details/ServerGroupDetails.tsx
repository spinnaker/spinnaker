import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { $q, $templateCache } from 'ngimport';

import { Application } from 'core/application';
import { AngularJSAdapter, ReactInjector } from 'core/reactShims';


export interface IServerGroupDetailsStateParams {
  provider: string;
  accountId: string;
  region: string;
  serverGroup: string;
}

export interface IServerGroupDetailsProps {
  app: Application;
  serverGroup: {
    name: string;
    accountId: string;
    region: string;
  };
}

export interface IServerGroupDetailsState {
  angular: {
    template: string,
    controller: string,
  };
  ServerGroupDetailsComponent: React.ComponentClass<IServerGroupDetailsProps>,
  accountId: string;
  provider: string;
  region: string;
}

@BindAll()
export class ServerGroupDetails extends React.Component<IServerGroupDetailsProps, IServerGroupDetailsState> {
  constructor(props: IServerGroupDetailsProps) {
    super(props);

    const $stateParams: IServerGroupDetailsStateParams = ReactInjector.$stateParams as any;

    this.state = {
      accountId: $stateParams.accountId,
      provider: $stateParams.provider,
      region: $stateParams.region,
      angular: {
        template: undefined,
        controller: undefined,
      },
      ServerGroupDetailsComponent: undefined,
    };
  }

  public componentDidMount(): void {
    const { provider, accountId } = this.state;
    const { versionedCloudProviderService } = ReactInjector;
    $q.all([
      versionedCloudProviderService.getValue(provider, accountId, 'serverGroup.detailsComponent'),
      versionedCloudProviderService.getValue(provider, accountId, 'serverGroup.detailsTemplateUrl'),
      versionedCloudProviderService.getValue(provider, accountId, 'serverGroup.detailsController'),
    ]).then((values: [React.ComponentClass<IServerGroupDetailsProps>, string, string]) => {
      const [ component, templateUrl, controller ] = values;
      const template = templateUrl ? $templateCache.get<string>(templateUrl) : undefined;
      this.setState({ angular: { template, controller }, ServerGroupDetailsComponent: component });
    });
  }

  public render() {
    const { app, serverGroup } = this.props;
    const { angular: { template, controller }, ServerGroupDetailsComponent } = this.state;

    if (ServerGroupDetailsComponent) {
      // react
      return <ServerGroupDetailsComponent app={app} serverGroup={serverGroup} />;
    }

    // angular
    if (template && controller) {
      return <AngularJSAdapter className="detail-content flex-container-h" template={template} controller={`${controller} as ctrl`} locals={{ app, serverGroup }} />
    }

    return null;
  }
}
