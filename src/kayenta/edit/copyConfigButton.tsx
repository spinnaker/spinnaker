import * as React from 'react';
import { UISref } from '@uirouter/react';
import { connect } from 'react-redux';
import { ICanaryState } from '../reducers/index';

interface ICopyConfigButtonStateProps {
  disabled: boolean;
}

/*
 * Button for copying a canary config.
 */
function CopyConfigButton({ disabled }: ICopyConfigButtonStateProps) {
  return (
    <UISref to="^.configDetail" params={{copy: true}}>
      <button className="passive" disabled={disabled}>
        <i className="fa fa-copy"/>
        <span>Copy</span>
      </button>
    </UISref>
  );
}

function mapStateToProps(state: ICanaryState) {
  return {
    disabled: state.selectedConfig.config && state.selectedConfig.config.isNew,
  }
}

export default connect(mapStateToProps)(CopyConfigButton);
