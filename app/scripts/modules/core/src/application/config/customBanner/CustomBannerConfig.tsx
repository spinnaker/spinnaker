import { isEqual } from 'lodash';
import React from 'react';
import Select, { Option } from 'react-select';

import { bannerBackgroundColorOptions, bannerTextColorOptions } from './customBannerColors';
import { ConfigSectionFooter } from '../footer/ConfigSectionFooter';
import { HelpField } from '../../../help/HelpField';
import { Markdown } from '../../../presentation/Markdown';
import { noop } from '../../../utils';

import './customBannerConfig.less';

export interface ICustomBannerConfig {
  backgroundColor: string;
  enabled: boolean;
  text: string;
  textColor: string;
}

export interface ICustomBannerConfigProps {
  bannerConfigs: ICustomBannerConfig[];
  isSaving: boolean;
  saveError: boolean;
  updateBannerConfigs: (bannerConfigs: ICustomBannerConfig[]) => void;
}

export interface ICustomBannerConfigState {
  bannerConfigsEditing: ICustomBannerConfig[];
}

export class CustomBannerConfig extends React.Component<ICustomBannerConfigProps, ICustomBannerConfigState> {
  public static defaultProps: Partial<ICustomBannerConfigProps> = {
    bannerConfigs: [],
    isSaving: false,
    saveError: false,
    updateBannerConfigs: noop,
  };

  constructor(props: ICustomBannerConfigProps) {
    super(props);
    this.state = {
      bannerConfigsEditing: props.bannerConfigs,
    };
  }

  private onEnabledChange = (idx: number, isChecked: boolean) => {
    this.setState({
      bannerConfigsEditing: this.state.bannerConfigsEditing.map((config, i) => {
        if (i === idx) {
          return {
            ...config,
            enabled: isChecked,
          };
        }
        // Only one config can be enabled
        return {
          ...config,
          enabled: isChecked ? false : config.enabled,
        };
      }),
    });
  };

  private onTextChange = (idx: number, text: string) => {
    this.setState({
      bannerConfigsEditing: this.state.bannerConfigsEditing.map((config, i) => {
        if (i === idx) {
          return {
            ...config,
            text,
          };
        }
        return config;
      }),
    });
  };

  private onTextColorChange = (idx: number, option: Option<string>) => {
    this.setState({
      bannerConfigsEditing: this.state.bannerConfigsEditing.map((config, i) => {
        if (i === idx) {
          return {
            ...config,
            textColor: option.value,
          };
        }
        return config;
      }),
    });
  };

  private onBackgroundColorChange = (idx: number, option: Option<string>) => {
    this.setState({
      bannerConfigsEditing: this.state.bannerConfigsEditing.map((config, i) => {
        if (i === idx) {
          return {
            ...config,
            backgroundColor: option.value,
          };
        }
        return config;
      }),
    });
  };

  private addBanner = (): void => {
    this.setState({
      bannerConfigsEditing: this.state.bannerConfigsEditing.concat([
        {
          backgroundColor: 'var(--color-alert)',
          enabled: false,
          text: 'Your custom banner text',
          textColor: 'var(--color-text-on-dark)',
        } as ICustomBannerConfig,
      ]),
    });
  };

  private removeBanner = (idx: number): void => {
    this.setState({
      bannerConfigsEditing: this.state.bannerConfigsEditing.filter((_config, i) => i !== idx),
    });
  };

  private isDirty = (): boolean => {
    return !isEqual(this.props.bannerConfigs, this.state.bannerConfigsEditing);
  };

  private onRevertClicked = (): void => {
    this.setState({
      bannerConfigsEditing: this.props.bannerConfigs,
    });
  };

  private onSaveClicked = (): void => {
    this.props.updateBannerConfigs(this.state.bannerConfigsEditing);
  };

  private colorOptionRenderer = (option: Option<string>): JSX.Element => {
    return <div className="custom-banner-config-color-option" style={{ backgroundColor: option.value }} />;
  };

  public render() {
    return (
      <div className="custom-banner-config-container">
        <div className="custom-banner-config-description">
          Custom Banners allow you to specify application-specific headers that will appear above the main Spinnaker
          navigation bar.
        </div>
        <div className="col-md-10 col-md-offset-1">
          <table className="table table-condensed">
            <thead>
              <tr>
                <th className="text-center">Enabled</th>
                <th>Text</th>
                <th className="custom-banner-config-color-option-column">Text Color</th>
                <th className="custom-banner-config-color-option-column">Background</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {this.state.bannerConfigsEditing.map((banner, idx) => (
                <tr key={idx} className="custom-banner-config-row">
                  <td className="text-center">
                    <input
                      checked={banner.enabled}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) => this.onEnabledChange(idx, e.target.checked)}
                      type="checkbox"
                    />
                  </td>
                  <td>
                    <textarea
                      className="form-control input-sm custom-banner-config-textarea"
                      onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => this.onTextChange(idx, e.target.value)}
                      style={{
                        backgroundColor: banner.backgroundColor,
                        color: banner.textColor,
                      }}
                      value={banner.text}
                    />
                    <div className="small text-right">
                      Markdown is okay <HelpField id="markdown.examples" />
                    </div>
                    <div>
                      <b>Preview</b>
                      <div
                        className="input-sm custom-banner-config-preview"
                        style={{
                          backgroundColor: banner.backgroundColor,
                          color: banner.textColor,
                        }}
                      >
                        <Markdown message={banner.text} />
                      </div>
                    </div>
                  </td>
                  <td>
                    <Select
                      clearable={false}
                      options={bannerTextColorOptions}
                      onChange={(option: Option<string>) => this.onTextColorChange(idx, option)}
                      optionRenderer={this.colorOptionRenderer}
                      value={banner.textColor}
                      valueRenderer={this.colorOptionRenderer}
                    />
                  </td>
                  <td>
                    <Select
                      clearable={false}
                      options={bannerBackgroundColorOptions}
                      onChange={(option: Option<string>) => this.onBackgroundColorChange(idx, option)}
                      optionRenderer={this.colorOptionRenderer}
                      value={banner.backgroundColor}
                      valueRenderer={this.colorOptionRenderer}
                    />
                  </td>
                  <td>
                    <button className="link custom-banner-config-remove" onClick={() => this.removeBanner(idx)}>
                      <span className="glyphicon glyphicon-trash" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="col-md-10 col-md-offset-1">
          <button className="btn btn-block add-new" onClick={this.addBanner}>
            <span className="glyphicon glyphicon-plus-sign" /> Add banner
          </button>
        </div>
        <ConfigSectionFooter
          isDirty={this.isDirty()}
          isValid={true}
          isSaving={this.props.isSaving}
          saveError={false}
          onRevertClicked={this.onRevertClicked}
          onSaveClicked={this.onSaveClicked}
        />
      </div>
    );
  }
}
