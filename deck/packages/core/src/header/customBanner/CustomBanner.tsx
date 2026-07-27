import { get } from 'lodash';
import React from 'react';

import type { ICustomBannerConfig } from '../../application/config/customBanner/CustomBannerConfig';
import { ApplicationReader } from '../../application/service/ApplicationReader';
import type { IRouterInjectedProps } from '../../navigation/routerContext';
import { withRouter } from '../../navigation/routerContext';
import { Markdown } from '../../presentation/Markdown';
import { noop } from '../../utils';

import './CustomBanner.less';

export interface ICustomBannerState {
  applicationName: string;
  bannerConfig: ICustomBannerConfig;
}

export class CustomBannerComponent extends React.Component<IRouterInjectedProps, ICustomBannerState> {
  private locationChangeUnsubscribe: Function;

  public state = {
    applicationName: null,
    bannerConfig: null,
  } as ICustomBannerState;

  public componentDidMount(): void {
    this.locationChangeUnsubscribe = this.props.router.transitionService.onSuccess({}, (transition) =>
      this.updateApplication(transition.params('to').application),
    );
  }

  private updateApplication(applicationName: string = this.props.stateParams.application): void {
    if (applicationName !== this.state.applicationName) {
      this.setState({
        applicationName,
        bannerConfig: null,
      });
      if (applicationName) {
        ApplicationReader.getApplicationAttributes(applicationName)
          .then((attributes: any) => {
            this.updateBannerConfig(attributes);
          })
          .catch(noop);
      }
    }
  }

  public updateBannerConfig(attributes: any): void {
    const bannerConfigs: ICustomBannerConfig[] = get(attributes, 'customBanners') || [];
    const bannerConfig = bannerConfigs.find((config) => config.enabled) || null;
    this.setState({
      bannerConfig,
    });
  }

  public render(): React.ReactElement<CustomBannerComponent> {
    const { bannerConfig } = this.state;
    if (bannerConfig == null) {
      return null;
    }
    return (
      <div
        className="custom-banner"
        style={{
          background: bannerConfig.backgroundColor,
          color: bannerConfig.textColor,
        }}
      >
        <Markdown message={bannerConfig.text} />
      </div>
    );
  }

  public componentWillUnmount(): void {
    this.locationChangeUnsubscribe();
  }
}

export const CustomBanner = withRouter(CustomBannerComponent);
CustomBanner.displayName = 'CustomBanner';
