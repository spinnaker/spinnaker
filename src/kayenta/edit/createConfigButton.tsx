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
    <UISref to=".configDetail" params={{id: UUIDGenerator.generateUuid(), 'new': true}}>
      <button className="zombie text-left form-control" disabled={disabled}>
        <i className="fa fa-plus-circle"/>
        <span>Add configuration</span>
      </button>
    </UISref>
  );
}

function mapStateToProps(state: ICanaryState): ICreateConfigButtonStateProps {
  return {
    disabled: state.selectedConfig.config && state.selectedConfig.config.isNew,
  };
}

export default connect(mapStateToProps)(CreateConfigButton);
