import * as React from 'react';
import { get } from 'lodash';

import { ICustomBannerConfig } from 'core/application/config/customBanner/CustomBannerConfig';
import { Application } from 'core/application/application.model';
import { ApplicationReader } from 'core/application/service/ApplicationReader';
import { ReactInjector } from 'core/reactShims';
import { noop } from 'core/utils';

import './CustomBanner.less';

export interface ICustomBannerState {
  applicationName: string;
  bannerConfig: ICustomBannerConfig;
}

export class CustomBanner extends React.Component<{}, ICustomBannerState> {
  private locationChangeUnsubscribe: Function;

  public state = {
    applicationName: null,
    bannerConfig: null,
  } as ICustomBannerState;

  public componentDidMount(): void {
    this.locationChangeUnsubscribe = ReactInjector.$uiRouter.transitionService.onSuccess({}, () =>
      this.updateApplication(),
    );
  }

  private updateApplication(): void {
    const applicationName: string = get(ReactInjector, '$stateParams.application');
    if (applicationName !== this.state.applicationName) {
      this.setState({
        applicationName,
        bannerConfig: null,
      });
      if (applicationName != null) {
        ApplicationReader.getApplication(applicationName)
          .then((app: Application) => {
            this.updateBannerConfig(app);
          })
          .catch(noop);
      }
    }
  }

  public updateBannerConfig(application: Application): void {
    const bannerConfigs: ICustomBannerConfig[] = get(application, 'attributes.customBanners') || [];
    const bannerConfig = bannerConfigs.find(config => config.enabled) || null;
    this.setState({
      bannerConfig,
    });
  }

  public render(): React.ReactElement<CustomBanner> {
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
        {bannerConfig.text}
      </div>
    );
  }

  public componentWillUnmount(): void {
    this.locationChangeUnsubscribe();
  }
}
