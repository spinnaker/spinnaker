import * as React from 'react';
import { get } from 'lodash';

import { ICustomBannerConfig } from 'core/application/config/customBanner/CustomBannerConfig';
import { ApplicationReader } from 'core/application/service/ApplicationReader';
import { Markdown } from 'core/presentation/Markdown';
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
        <Markdown message={bannerConfig.text} />
      </div>
    );
  }

  public componentWillUnmount(): void {
    this.locationChangeUnsubscribe();
  }
}
