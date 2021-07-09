import { get, without } from 'lodash';
import React from 'react';
import Select, { Option } from 'react-select';

import { ILabelFilter } from './labelFilterUtils';
import { noop } from '../../utils';

import './LabelFilter.less';

export interface ILabelFilterProps {
  labelsMap: { [key: string]: string[] };
  labelFilters: ILabelFilter[];
  updateLabelFilters: (labelFilters: ILabelFilter[]) => void;
}

export default class LabelFilter extends React.Component<ILabelFilterProps> {
  public static defaultProps: ILabelFilterProps = {
    labelsMap: {},
    labelFilters: [],
    updateLabelFilters: noop,
  };

  private onAdd = (): void => {
    if (this.props.labelFilters.some((l) => l.key === null)) {
      return;
    }
    const newFilter: ILabelFilter = {
      key: null,
      value: null,
    };
    this.props.updateLabelFilters(this.props.labelFilters.concat([newFilter]));
  };

  private onDelete = (idx: number): void => {
    this.props.updateLabelFilters(this.props.labelFilters.filter((_e, i) => i !== idx));
  };

  private handleKeyChange = (option: Option<string>, idx: number) => {
    const nextLabelFilters = this.props.labelFilters.map((filter, i) => {
      if (i !== idx) {
        return filter;
      }
      return {
        key: option.value,
        value: null,
      };
    });
    this.props.updateLabelFilters(nextLabelFilters);
  };

  private handleValueChange = (option: Option<string>, idx: number) => {
    const nextLabelFilters = this.props.labelFilters.map((filter, i) => {
      if (i !== idx) {
        return filter;
      }
      return {
        key: filter.key,
        value: option.value,
      };
    });
    this.props.updateLabelFilters(nextLabelFilters);
  };

  private getKeyOptions = (idx: number): Array<Option<string>> => {
    const allLabelKeys = Object.keys(this.props.labelsMap);
    const otherFilters = this.props.labelFilters.filter((_e, i) => i !== idx);
    const availableKeys = without(allLabelKeys, ...otherFilters.map((e) => e.key));
    return availableKeys.map((key: string) => ({ label: key, value: key }));
  };

  private getValueOptions = (key: string): Array<Option<string>> => {
    const values = get(this.props.labelsMap, key, []);
    return values.map((val) => ({ label: val, value: val }));
  };

  public render() {
    return (
      <div className="label-filter">
        {this.props.labelFilters.map(({ key, value }, idx) => (
          <LabelFilterSelect
            handleKeyChange={(option: Option<string>) => this.handleKeyChange(option, idx)}
            handleValueChange={(option: Option<string>) => this.handleValueChange(option, idx)}
            key={idx}
            keyOptions={this.getKeyOptions(idx)}
            onDelete={() => this.onDelete(idx)}
            selectedKey={key}
            selectedValue={value}
            valueOptions={this.getValueOptions(key)}
          />
        ))}
        <button type="button" className="btn btn-block btn-sm add-new" onClick={this.onAdd}>
          <span className="glyphicon glyphicon-plus-sign" />
          Add label filter
        </button>
      </div>
    );
  }
}

interface ILabelFilterSelectProps {
  handleKeyChange: (option: Option<string>) => void;
  handleValueChange: (options: Option<string>) => void;
  keyOptions: Array<Option<string>>;
  onDelete: () => void;
  selectedKey: string;
  selectedValue: string;
  valueOptions: Array<Option<string>>;
}

export const LabelFilterSelect = ({
  handleKeyChange,
  handleValueChange,
  keyOptions,
  onDelete,
  selectedKey,
  selectedValue,
  valueOptions,
}: ILabelFilterSelectProps) => {
  return (
    <div className="label-filter-select-container">
      <div className="label-filter-select">
        <Select
          autosize={false}
          clearable={false}
          onChange={handleKeyChange}
          options={keyOptions}
          placeholder="Select key..."
          value={selectedKey}
        />
        <Select
          autosize={false}
          clearable={false}
          onChange={handleValueChange}
          options={valueOptions}
          placeholder="Select value..."
          value={selectedValue}
        />
      </div>
      <div className="label-filter-remove">
        <button className="link" onClick={onDelete}>
          <span className="glyphicon glyphicon-trash" />
        </button>
      </div>
    </div>
  );
};
