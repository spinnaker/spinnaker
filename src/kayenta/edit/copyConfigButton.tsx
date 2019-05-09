import * as React from 'react';
import { UISref } from '@uirouter/react';
import { connect } from 'react-redux';
import { get } from 'lodash';

import { ICanaryState } from '../reducers/index';

interface ICopyConfigButtonStateProps {
  disabled: boolean;
}

/*
 * Button for copying a canary config.
 */
function CopyConfigButton({ disabled }: ICopyConfigButtonStateProps) {
  return (
    <UISref to="^.configDetail" params={{ copy: true }}>
      <button className="passive" disabled={disabled}>
        <i className="fa fa-copy" />
        <span>Copy</span>
      </button>
    </UISref>
  );
}

function mapStateToProps(state: ICanaryState) {
  return {
    disabled: get(state.selectedConfig, 'config.isNew') || state.app.disableConfigEdit,
  };
}

export default connect(mapStateToProps)(CopyConfigButton);
