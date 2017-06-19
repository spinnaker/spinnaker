import * as React from 'react';
import * as Select from 'react-select';
import autoBindMethods from 'class-autobind-decorator'

import { HelpField } from '@spinnaker/core';

export interface ICustomInstanceConfig {
  vCpuCount: number;
  memory: number;
}

export interface ICustomInstanceConfigurerProps {
  vCpuList: number[];
  memoryList: number[];
  selectedVCpuCount: number;
  selectedMemory: number;
  onChange: (config: ICustomInstanceConfig) => void;
}

@autoBindMethods()
export class CustomInstanceConfigurer extends React.Component<ICustomInstanceConfigurerProps, null> {
  public render() {
    const vCpuOptions: Select.Option[] = (this.props.vCpuList || []).map(vCpu => ({label: vCpu + '', value: vCpu}));
    const memoryOptions: Select.Option[] = (this.props.memoryList || []).map(memory => ({label: memory + '', value: memory}));
    const selectedVCpuCountLabel = this.props.selectedVCpuCount ? this.props.selectedVCpuCount + '' : null;
    const selectedMemoryLabel = this.props.selectedMemory ? this.props.selectedMemory + '' : null;

    return (
      <div>
        <div className="row">
          <div className="col-md-5 sm-label-right">
            <b>Cores </b>
            <HelpField id="gce.instance.customInstance.cores"/>
          </div>
          <div className="col-md-3">
            <Select
              options={vCpuOptions}
              clearable={false}
              value={{label: selectedVCpuCountLabel, value: this.props.selectedVCpuCount}}
              onChange={this.handleVCpuChange}
            />
          </div>
        </div>
        <div className="row" style={{marginTop: '5px'}}>
          <div className="col-md-5 sm-label-right">
            <b>Memory (Gb) </b>
            <HelpField id="gce.instance.customInstance.memory"/>
          </div>
          <div className="col-md-3">
            <Select
              options={memoryOptions}
              clearable={false}
              value={{label: selectedMemoryLabel, value: this.props.selectedMemory}}
              onChange={this.handleMemoryChange}
            />
          </div>
        </div>
      </div>
    );
  }

  private handleVCpuChange(option: Select.Option) {
    const value = (option ? option.value : null) as number;
    this.props.onChange({vCpuCount: value, memory: this.props.selectedMemory});
  }

  private handleMemoryChange(option: Select.Option) {
    const value = (option ? option.value : null) as number;
    this.props.onChange({vCpuCount: this.props.selectedVCpuCount, memory: value});
  }
}
