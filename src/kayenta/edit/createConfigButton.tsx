import * as React from 'react';
import { UISref } from '@uirouter/react';
import { connect } from 'react-redux';
import { ICanaryState } from '../reducers/index';
import { UUIDGenerator } from '@spinnaker/core';

interface ICreateConfigButtonStateProps {
  disabled: boolean;
}

/*
* Button for creating a new canary config.
*/
function CreateConfigButton({ disabled }: ICreateConfigButtonStateProps) {
  return (
    <UISref to=".configDetail" params={{configName: UUIDGenerator.generateUuid(), 'new': true}}>
      <button className="passive" disabled={disabled}>Add configuration</button>
    </UISref>
  );
}

function mapStateToProps(state: ICanaryState): ICreateConfigButtonStateProps {
  return {
    disabled: state.selectedConfig.config && state.selectedConfig.config.isNew,
  };
}

export default connect(mapStateToProps)(CreateConfigButton);
