import React from 'react';
import type { Option } from 'react-select';
import Select from 'react-select';

import { CheckboxInput, HelpField } from '@spinnaker/core';
import './customInstanceConfigurer.component.less';

export interface ICustomInstanceConfig {
  vCpuCount: number;
  memory: number;
  instanceFamily: string;
  extendedMemory: boolean;
}

export interface ICustomInstanceConfigurerProps {
  vCpuList: number[];
  memoryList: number[];
  instanceFamilyList: string[];
  selectedVCpuCount: number;
  selectedMemory: number;
  selectedInstanceFamily: string;
  selectedExtendedMemory: boolean;
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
        <div className="row gce-instance-build-custom-select">
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
        <div className="row gce-instance-build-custom-select">
          <div className="col-md-5 sm-label-right">
            <b>Memory (Gb) </b>
            <HelpField id="gce.instance.customInstance.memory" />
          </div>
          <div className="col-md-3">
            {this.props.selectedExtendedMemory ? (
              <input
                type="number"
                name="memory"
                className="form-control"
                value={this.props.selectedMemory}
                onChange={(event) => this.handleMemoryChangeCustom(event.target.value)}
              />
            ) : (
              <Select
                options={memoryOptions}
                clearable={false}
                value={{ label: selectedMemoryLabel, value: this.props.selectedMemory }}
                onChange={this.handleMemoryChange}
              />
            )}
          </div>
        </div>
        {this.props.selectedInstanceFamily !== 'E2' ? (
          <div className="row gce-instance-build-custom-select">
            <div className="col-md-5 sm-label-right"></div>
            <div className="col-md-3">
              <span className="gce-instance-build-custom-extended-memory-checkbox">
                <CheckboxInput
                  name="extendedMemory"
                  text="Extended Memory"
                  checked={this.props.selectedExtendedMemory}
                  onChange={(event: { target: { checked: boolean } }) => {
                    this.handleExtendedMemory(event.target.checked);
                  }}
                />
                <div className="gce-instance-build-custom-extended-memory-checkbox-helptext">
                  <HelpField id="gce.instance.customInstance.extendedmemory" />
                </div>
              </span>
            </div>
          </div>
        ) : null}
      </div>
    );
  }

  private handleVCpuChange = (option: Option) => {
    const value = (option ? option.value : null) as number;
    this.props.onChange({
      instanceFamily: this.props.selectedInstanceFamily,
      vCpuCount: value,
      memory: this.props.selectedMemory,
      extendedMemory: this.props.selectedExtendedMemory,
    });
  };

  private handleMemoryChange = (option: Option) => {
    const value = (option ? option.value : null) as number;
    this.props.onChange({
      instanceFamily: this.props.selectedInstanceFamily,
      vCpuCount: this.props.selectedVCpuCount,
      memory: value,
      extendedMemory: this.props.selectedExtendedMemory,
    });
  };

  private handleInstanceFamilyChange = (option: Option) => {
    const value = (option ? option.value : null) as string;
    this.props.onChange({
      instanceFamily: value,
      vCpuCount: this.props.selectedVCpuCount,
      memory: this.props.selectedMemory,
      extendedMemory: value === 'E2' ? false : this.props.selectedExtendedMemory,
    });
  };

  private handleMemoryChangeCustom = (val: string) => {
    const value = +val;
    this.props.onChange({
      instanceFamily: this.props.selectedInstanceFamily,
      vCpuCount: this.props.selectedVCpuCount,
      memory: value,
      extendedMemory: this.props.selectedExtendedMemory,
    });
  };

  private handleExtendedMemory = (checked: boolean) => {
    this.props.onChange({
      instanceFamily: this.props.selectedInstanceFamily,
      vCpuCount: this.props.selectedVCpuCount,
      memory: this.props.selectedMemory,
      extendedMemory: checked,
    });
  };
}
