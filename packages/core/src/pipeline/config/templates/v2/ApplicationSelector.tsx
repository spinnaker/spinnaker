import React from 'react';
import Select, { Option } from 'react-select';

import { IApplicationSummary } from '../../../../application';

interface IApplicatonSelectorProps {
  applications: IApplicationSummary[];
  applicationSelectCallback: (selection: Option) => void;
  selectedApplication: IApplicationSummary;
}

export default class ApplicationSelector extends React.Component<IApplicatonSelectorProps> {
  public render() {
    const { applications, applicationSelectCallback, selectedApplication } = this.props;

    const selectedApplicationOption: Option = selectedApplication
      ? { label: selectedApplication.name, value: selectedApplication.name }
      : null;

    return (
      <div className="form-group clearfix">
        <div className="col-md-3 sm-label-right">
          <b>Application</b>
        </div>
        <div className="col-md-7">
          <Select
            options={applications.map(({ name }: IApplicationSummary): Option => ({ label: name, value: name }))}
            clearable={true}
            value={selectedApplicationOption}
            onChange={applicationSelectCallback}
            onSelectResetsInput={false}
          />
        </div>
      </div>
    );
  }
}
