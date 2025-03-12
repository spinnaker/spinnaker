import React from 'react';

import { FormikFormField, TextInput } from '@spinnaker/core';

export interface IInstanceParametersProps {
  diskQuotaFieldName: string;
  instancesFieldName: string;
  memoryFieldName: string;
  onDiskQuotaChange?: (value: string) => void;
  onInstancesChange?: (value: string) => void;
  onMemoryChange?: (value: string) => void;
}

export class InstanceParameters extends React.Component<IInstanceParametersProps> {
  public render() {
    const {
      diskQuotaFieldName,
      instancesFieldName,
      memoryFieldName,
      onDiskQuotaChange,
      onInstancesChange,
      onMemoryChange,
    } = this.props;

    return (
      <div>
        <div className="col-md-9">
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name={memoryFieldName}
              input={(props) => <TextInput {...props} />}
              label="Memory"
              onChange={(value) => {
                onMemoryChange && onMemoryChange(value);
              }}
            />
          </div>
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name={diskQuotaFieldName}
              input={(props) => <TextInput {...props} />}
              label="Disk Quota"
              onChange={(value) => {
                onDiskQuotaChange && onDiskQuotaChange(value);
              }}
            />
          </div>
          <div className="sp-margin-m-bottom">
            <FormikFormField
              name={instancesFieldName}
              input={(props) => <TextInput type="number" {...props} />}
              label="Instances"
              onChange={(value) => {
                onInstancesChange && onInstancesChange(value);
              }}
              required={true}
            />
          </div>
        </div>
      </div>
    );
  }
}
