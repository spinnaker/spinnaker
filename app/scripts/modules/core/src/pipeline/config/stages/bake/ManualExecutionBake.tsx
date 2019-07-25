import * as React from 'react';
import { get } from 'lodash';

import { HelpField } from 'core/help/HelpField';
import { ITriggerTemplateComponentProps } from 'core/pipeline/manualExecution/TriggerTemplate';

export class ManualExecutionBake extends React.Component<ITriggerTemplateComponentProps> {
  private handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const target = event.target;
    const checked = target.checked;
    this.props.updateCommand('trigger.rebake', checked);
    this.setState({});
  };

  public render() {
    const force = get(this.props.command, 'trigger.rebake', false);

    return (
      <div className="form-group">
        <label className="col-md-4 sm-label-right">Rebake</label>
        <div className="checkbox col-md-6">
          <label>
            <input type="checkbox" checked={force} onChange={this.handleChange} />
            Force Rebake
          </label>{' '}
          <HelpField id="execution.forceRebake" />
        </div>
      </div>
    );
  }
}
