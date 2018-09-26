import * as React from 'react';
import Select, { Option } from 'react-select';

import { HelpField } from 'core/help/HelpField';

export interface IRunAsUserProps {
  serviceAccounts: string[];
  value: string;
  onChange: (value: string) => void;
}

export class RunAsUser extends React.Component<IRunAsUserProps> {
  public render() {
    const { serviceAccounts, onChange, value } = this.props;

    const serviceAccountOptions = serviceAccounts.map(a => ({ label: a, value: a }));
    return (
      <div>
        <div className="col-md-3 sm-label-right">
          Run As User
          <HelpField id="pipeline.config.trigger.runAsUser" />
        </div>
        <div className="col-md-9">
          <Select
            className="form-control input-sm"
            options={serviceAccountOptions}
            value={value || ''}
            onChange={(o: Option<string>) => onChange(o.value)}
            placeholder="Select Run As User"
          />
        </div>
      </div>
    );
  }
}
