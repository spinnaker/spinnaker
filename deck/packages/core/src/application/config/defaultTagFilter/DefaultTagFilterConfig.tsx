import { isEqual } from 'lodash';
import React from 'react';

import { ConfigSectionFooter } from '../footer/ConfigSectionFooter';
import { noop } from '../../../utils';

import './defaultTagFilterConfig.less';

export interface IDefaultTagFilterConfig {
  tagName: string;
  tagValue: string;
}

export interface IDefaultTagFilterProps {
  defaultTagFilterConfigs: IDefaultTagFilterConfig[];
  isSaving: boolean;
  saveError: boolean;
  updateDefaultTagFilterConfigs: (defaultTagFilterConfigs: IDefaultTagFilterConfig[]) => void;
}

export interface IDefaultTagFilterState {
  defaultTagFilterConfigsEditing: IDefaultTagFilterConfig[];
}

export class DefaultTagFilterConfig extends React.Component<IDefaultTagFilterProps, IDefaultTagFilterState> {
  public static defaultProps: Partial<IDefaultTagFilterProps> = {
    defaultTagFilterConfigs: [],
    isSaving: false,
    saveError: false,
    updateDefaultTagFilterConfigs: noop,
  };

  constructor(props: IDefaultTagFilterProps) {
    super(props);
    this.state = {
      defaultTagFilterConfigsEditing: props.defaultTagFilterConfigs,
    };
  }

  private onTagNameChange = (idx: number, text: string) => {
    this.setState({
      defaultTagFilterConfigsEditing: this.state.defaultTagFilterConfigsEditing.map((config, i) => {
        if (i === idx) {
          return {
            ...config,
            tagName: text,
          };
        }
        return config;
      }),
    });
  };

  private onTagValueChange = (idx: number, text: string) => {
    this.setState({
      defaultTagFilterConfigsEditing: this.state.defaultTagFilterConfigsEditing.map((config, i) => {
        if (i === idx) {
          return {
            ...config,
            tagValue: text,
          };
        }
        return config;
      }),
    });
  };

  private addFilterTag = (): void => {
    this.setState({
      defaultTagFilterConfigsEditing: this.state.defaultTagFilterConfigsEditing.concat([
        {
          tagName: 'Name of the tag (E.g. Pipeline Type)',
          tagValue: 'Value of the tag (E.g. Default Pipelines)',
        } as IDefaultTagFilterConfig,
      ]),
    });
  };

  private removeFilterTag = (idx: number): void => {
    this.setState({
      defaultTagFilterConfigsEditing: this.state.defaultTagFilterConfigsEditing.filter((_config, i) => i !== idx),
    });
  };

  private isDirty = (): boolean => {
    return !isEqual(this.props.defaultTagFilterConfigs, this.state.defaultTagFilterConfigsEditing);
  };

  private onRevertClicked = (): void => {
    this.setState({
      defaultTagFilterConfigsEditing: this.props.defaultTagFilterConfigs,
    });
  };

  private onSaveClicked = (): void => {
    this.props.updateDefaultTagFilterConfigs(this.state.defaultTagFilterConfigsEditing);
  };

  public render() {
    return (
      <div className="default-filter-config-container">
        <div className="default-filter-config-description">
          Default Tag filters allow you to specify which tags are immediately filtered to when the pipeline execution
          page is loaded in.
        </div>
        <div className="col-md-10 col-md-offset-1">
          <table className="table table-condensed">
            <thead>
              <tr>
                <th>Tag Name</th>
                <th>Tag Value</th>
              </tr>
            </thead>
            <tbody>
              {this.state.defaultTagFilterConfigsEditing.map((defaultTagFilter, idx) => (
                <tr key={idx} className="default-filter-config-row">
                  <td>
                    <textarea
                      className="form-control input-sm"
                      onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) =>
                        this.onTagNameChange(idx, e.target.value)
                      }
                      value={defaultTagFilter.tagName}
                    />
                  </td>
                  <td>
                    <textarea
                      className="form-control input-sm"
                      onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) =>
                        this.onTagValueChange(idx, e.target.value)
                      }
                      value={defaultTagFilter.tagValue}
                    />
                  </td>
                  <td>
                    <button className="link default-filter-config-remove" onClick={() => this.removeFilterTag(idx)}>
                      <span className="glyphicon glyphicon-trash" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="col-md-10 col-md-offset-1">
          <button className="btn btn-block add-new" onClick={this.addFilterTag}>
            <span className="glyphicon glyphicon-plus-sign" /> Add Default Filter
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
