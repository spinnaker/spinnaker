import { get, without } from 'lodash';
import React from 'react';
import type { Option } from 'react-select';
import Select from 'react-select';

import type { ILabelFilter } from './labelFilterUtils';
import { noop } from '../../utils';

import './LabelFilter.less';

export interface ILabelFilterProps {
  labelsMap: { [key: string]: string[] };
  labelFilters: ILabelFilter[];
  updateLabelFilters: (labelFilters: ILabelFilter[]) => void;
}

export const getLabelKeyOptions = (
  labelsMap: { [key: string]: string[] },
  labelFilters: ILabelFilter[],
  index: number,
): Array<Option<string>> => {
  const otherFilters = labelFilters.filter((_filter, filterIndex) => filterIndex !== index);
  return without(Object.keys(labelsMap), ...otherFilters.map((filter) => filter.key)).map((key) => ({
    label: key,
    value: key,
  }));
};

export const getLabelValueOptions = (labelsMap: { [key: string]: string[] }, key: string): Array<Option<string>> =>
  get(labelsMap, key, []).map((value) => ({ label: value, value }));

export const updateLabelFilterKey = (labelFilters: ILabelFilter[], index: number, key: string): ILabelFilter[] =>
  labelFilters.map((filter, filterIndex) => (filterIndex === index ? { key, value: null } : filter));

export const updateLabelFilterValue = (labelFilters: ILabelFilter[], index: number, value: string): ILabelFilter[] =>
  labelFilters.map((filter, filterIndex) => (filterIndex === index ? { key: filter.key, value } : filter));

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
    this.props.updateLabelFilters(updateLabelFilterKey(this.props.labelFilters, idx, option.value));
  };

  private handleValueChange = (option: Option<string>, idx: number) => {
    this.props.updateLabelFilters(updateLabelFilterValue(this.props.labelFilters, idx, option.value));
  };

  private getKeyOptions = (idx: number): Array<Option<string>> => {
    return getLabelKeyOptions(this.props.labelsMap, this.props.labelFilters, idx);
  };

  private getValueOptions = (key: string): Array<Option<string>> => {
    return getLabelValueOptions(this.props.labelsMap, key);
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
        <button
          aria-label={`Remove ${selectedKey || 'empty'} label filter`}
          className="link"
          onClick={onDelete}
          type="button"
        >
          <span className="glyphicon glyphicon-trash" />
        </button>
      </div>
    </div>
  );
};
