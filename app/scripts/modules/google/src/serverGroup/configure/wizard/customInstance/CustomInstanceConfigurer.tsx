import React from 'react';
import Select, { Option } from 'react-select';

import { HelpField } from '@spinnaker/core';

export interface ICustomInstanceConfig {
  vCpuCount: number;
  memory: number;
  instanceFamily: string;
}

export interface ICustomInstanceConfigurerProps {
  vCpuList: number[];
  memoryList: number[];
  instanceFamilyList: string[];
  selectedVCpuCount: number;
  selectedMemory: number;
  selectedInstanceFamily: string;
  onChange: (config: ICustomInstanceConfig) => void;
}

export class CustomInstanceConfigurer extends React.Component<ICustomInstanceConfigurerProps> {
  public render() {
    const instanceFamilyOptions: Option[] = (this.props.instanceFamilyList || []).map((instanceFamily) => ({
      label: instanceFamily,
      value: instanceFamily,
    }));
    const vCpuOptions: Option[] = (this.props.vCpuList || []).map((vCpu) => ({ label: vCpu + '', value: vCpu }));
    const memoryOptions: Option[] = (this.props.memoryList || []).map((memory) => ({
      label: memory + '',
      value: memory,
    }));
    const selectedVCpuCountLabel = this.props.selectedVCpuCount ? this.props.selectedVCpuCount + '' : null;
    const selectedMemoryLabel = this.props.selectedMemory ? this.props.selectedMemory + '' : null;
    const selectedInstanceFamilyLabel = this.props.selectedInstanceFamily ? this.props.selectedInstanceFamily : null;

    return (
      <div>
        <div className="row">
          <div className="col-md-5 sm-label-right">
            <b>Family </b>
          </div>
          <div className="col-md-3">
            <Select
              options={instanceFamilyOptions}
              clearable={false}
              value={{ label: selectedInstanceFamilyLabel, value: this.props.selectedInstanceFamily }}
              onChange={this.handleInstanceFamilyChange}
            />
          </div>
        </div>
        <div className="row">
          <div className="col-md-5 sm-label-right">
            <b>Cores </b>
            <HelpField id="gce.instance.customInstance.cores" />
          </div>
          <div className="col-md-3">
            <Select
              options={vCpuOptions}
              clearable={false}
              value={{ label: selectedVCpuCountLabel, value: this.props.selectedVCpuCount }}
              onChange={this.handleVCpuChange}
            />
          </div>
        </div>
        <div className="row" style={{ marginTop: '5px' }}>
          <div className="col-md-5 sm-label-right">
            <b>Memory (Gb) </b>
            <HelpField id="gce.instance.customInstance.memory" />
          </div>
          <div className="col-md-3">
            <Select
              options={memoryOptions}
              clearable={false}
              value={{ label: selectedMemoryLabel, value: this.props.selectedMemory }}
              onChange={this.handleMemoryChange}
            />
          </div>
        </div>
      </div>
    );
  }

  private handleVCpuChange = (option: Option) => {
    const value = (option ? option.value : null) as number;
    this.props.onChange({
      instanceFamily: this.props.selectedInstanceFamily,
      vCpuCount: value,
      memory: this.props.selectedMemory,
    });
  };

  private handleMemoryChange = (option: Option) => {
    const value = (option ? option.value : null) as number;
    this.props.onChange({
      instanceFamily: this.props.selectedInstanceFamily,
      vCpuCount: this.props.selectedVCpuCount,
      memory: value,
    });
  };

  private handleInstanceFamilyChange = (option: Option) => {
    const value = (option ? option.value : null) as string;
    this.props.onChange({
      instanceFamily: value,
      vCpuCount: this.props.selectedVCpuCount,
      memory: this.props.selectedMemory,
    });
  };
}
