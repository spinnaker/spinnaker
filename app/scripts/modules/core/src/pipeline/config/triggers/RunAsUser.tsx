import * as React from 'react';
import Select, { Option } from 'react-select';

import { HelpField } from 'core/help/HelpField';

export interface IRunAsUserProps {
  serviceAccounts: string[];
  value: string;
  onChange: (value: string) => void;
  selectClasses?: string;
  selectColumns?: number;
}

export class RunAsUser extends React.Component<IRunAsUserProps> {
  public static defaultProps = {
    selectClasses: 'form-control input-sm',
    selectColumns: 9,
  };

  public render() {
    const { serviceAccounts, onChange, value, selectClasses, selectColumns } = this.props;

    const serviceAccountOptions = serviceAccounts.map(a => ({ label: a, value: a }));
    return (
      <div>
        <div className="col-md-3 sm-label-right">
          <span className="label-text">Run As User </span>
          <HelpField id="pipeline.config.trigger.runAsUser" />
        </div>
        <div className={'col-md-' + selectColumns}>
          <Select
            className={selectClasses}
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
