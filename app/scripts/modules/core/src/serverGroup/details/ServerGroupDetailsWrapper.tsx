import * as React from 'react';
import { $q, $templateCache } from 'ngimport';
import { Observable } from 'rxjs';

import { Application } from 'core/application';
import { IServerGroup } from 'core/domain';
import { AngularJSAdapter, ReactInjector } from 'core/reactShims';
import { SkinService } from 'core/cloudProvider/skin.service';

import { ServerGroupDetails } from './ServerGroupDetails';

export interface IServerGroupDetailsWrapperProps {
  app: Application;
  serverGroup: {
    name: string;
    accountId: string;
    region: string;
  };
}

export type DetailsGetter = (props: IServerGroupDetailsProps, autoClose: () => void) => Observable<IServerGroup>;

export interface IServerGroupDetailsWrapperState {
  angular: {
    template: string;
    controller: string;
  };
  detailsGetter: DetailsGetter;
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
  constructor(props: IServerGroupDetailsWrapperProps) {
    super(props);

    this.state = {
      angular: {
        template: undefined,
        controller: undefined,
      },
      Actions: undefined,
      detailsGetter: undefined,
      sections: [],
    };
  }

  private getServerGroupDetailsTemplate(): void {
    const { provider, accountId } = ReactInjector.$stateParams;
    $q.all([
      SkinService.getValue(provider, accountId, 'serverGroup.detailsActions'),
      SkinService.getValue(provider, accountId, 'serverGroup.detailsGetter'),
      SkinService.getValue(provider, accountId, 'serverGroup.detailsSections'),
      SkinService.getValue(provider, accountId, 'serverGroup.detailsTemplateUrl'),
      SkinService.getValue(provider, accountId, 'serverGroup.detailsController'),
    ]).then(
      (
        values: [
          React.ComponentClass<IServerGroupActionsProps>,
          DetailsGetter,
          Array<React.ComponentType<IServerGroupDetailsSectionProps>>,
          string,
          string
        ],
      ) => {
        const [Actions, detailsGetter, sections, templateUrl, controller] = values;
        const template = templateUrl ? $templateCache.get<string>(templateUrl) : undefined;
        this.setState({ angular: { template, controller }, Actions, detailsGetter, sections });
      },
    );
  }

  public componentDidMount(): void {
    this.getServerGroupDetailsTemplate();
  }

  public componentWillReceiveProps(): void {
    this.getServerGroupDetailsTemplate();
  }

  public render() {
    const { app, serverGroup } = this.props;
    const {
      angular: { template, controller },
      Actions,
      detailsGetter,
      sections,
    } = this.state;

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

    // angular
    if (template && controller) {
      return (
        <AngularJSAdapter
          className="detail-content flex-container-h"
          template={template}
          controller={`${controller} as ctrl`}
          locals={{ app, serverGroup }}
        />
      );
    }

    return null;
  }
}
