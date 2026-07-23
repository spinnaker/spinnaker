import React from 'react';
import type { Observable } from 'rxjs';

import { ServerGroupDetails } from './ServerGroupDetails';
import type { Application } from '../../application';
import { CloudProviderRegistry } from '../../cloudProvider';
import type { IServerGroup } from '../../domain';

export interface IServerGroupDetailsWrapperProps {
  app: Application;
  serverGroup: {
    name: string;
    accountId: string;
    provider: string;
    region: string;
  };
}

export type DetailsGetter = (props: IServerGroupDetailsProps, autoClose: () => void) => Observable<IServerGroup>;

export interface IServerGroupDetailsWrapperState {
  detailsGetter: DetailsGetter;
  legacyDetailsConfigured: boolean;
  sections: Array<React.ComponentType<IServerGroupDetailsSectionProps>>;
  Actions: React.ComponentType<IServerGroupActionsProps>;
}

export interface IServerGroupActionsProps {
  app: Application;
  serverGroup: IServerGroup;
}

export interface IServerGroupDetailsSectionProps {
  app: Application;
  serverGroup: IServerGroup;
}

export interface IServerGroupDetailsProps extends IServerGroupDetailsWrapperProps {
  Actions: React.ComponentType<IServerGroupActionsProps>;
  detailsGetter: DetailsGetter;
  sections: Array<React.ComponentType<IServerGroupDetailsSectionProps>>;
}

export interface IServerGroupDetailsState {
  loading: boolean;
  serverGroup: IServerGroup;
}

export class ServerGroupDetailsWrapper extends React.Component<
  IServerGroupDetailsWrapperProps,
  IServerGroupDetailsWrapperState
> {
  private configurationRequestId = 0;

  constructor(props: IServerGroupDetailsWrapperProps) {
    super(props);

    this.state = {
      Actions: undefined,
      detailsGetter: undefined,
      legacyDetailsConfigured: false,
      sections: [],
    };
  }

  private getServerGroupDetailsTemplate(serverGroup: IServerGroupDetailsWrapperProps['serverGroup']): void {
    const requestId = ++this.configurationRequestId;
    const { provider } = serverGroup;
    Promise.all([
      CloudProviderRegistry.getValue(provider, 'serverGroup.detailsActions'),
      CloudProviderRegistry.getValue(provider, 'serverGroup.detailsGetter'),
      CloudProviderRegistry.getValue(provider, 'serverGroup.detailsSections'),
      CloudProviderRegistry.getValue(provider, 'serverGroup.detailsTemplateUrl'),
      CloudProviderRegistry.getValue(provider, 'serverGroup.detailsController'),
    ]).then(
      (
        values: [
          React.ComponentClass<IServerGroupActionsProps>,
          DetailsGetter,
          Array<React.ComponentType<IServerGroupDetailsSectionProps>>,
          string,
          string,
        ],
      ) => {
        const [Actions, detailsGetter, sections, templateUrl, controller] = values;
        if (requestId === this.configurationRequestId) {
          this.setState({ Actions, detailsGetter, legacyDetailsConfigured: !!(templateUrl && controller), sections });
        }
      },
    );
  }

  public componentDidMount(): void {
    this.getServerGroupDetailsTemplate(this.props.serverGroup);
  }

  public componentWillReceiveProps(nextProps: IServerGroupDetailsWrapperProps): void {
    if (nextProps.serverGroup.provider !== this.props.serverGroup.provider) {
      this.setState({ Actions: undefined, detailsGetter: undefined, legacyDetailsConfigured: false, sections: [] });
      this.getServerGroupDetailsTemplate(nextProps.serverGroup);
    }
  }

  public render() {
    const { app, serverGroup } = this.props;
    const { Actions, detailsGetter, legacyDetailsConfigured, sections } = this.state;

    if (Actions && detailsGetter && sections) {
      // react
      return (
        <ServerGroupDetails
          app={app}
          serverGroup={serverGroup}
          sections={sections}
          Actions={Actions}
          detailsGetter={detailsGetter}
        />
      );
    }

    if (legacyDetailsConfigured) {
      return (
        <div className="alert alert-warning">
          Server group details must be migrated to React. AngularJS templates/controllers are no longer supported.
        </div>
      );
    }

    return null;
  }
}
