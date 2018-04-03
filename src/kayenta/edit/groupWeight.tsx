import * as React from 'react';
import { connect } from 'react-redux';
import { Action } from 'redux';
import { get, isNumber } from 'lodash';

import FormRow from '../layout/formRow';
import { DisableableInput, DISABLE_EDIT_CONFIG } from 'kayenta/layout/disableable';
import { ICanaryState } from 'kayenta/reducers';
import { ICanaryConfig } from 'kayenta/domain/ICanaryConfig';
import * as Creators from 'kayenta/actions/creators';
import { mapStateToConfig } from 'kayenta/service/canaryConfig.service';

export interface IGroupWeightOwnProps {
  group: string;
}

interface IGroupWeightStateProps {
  config: ICanaryConfig;
}

interface IGroupWeightDispatchProps {
  handleInputChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
}

/*
* Component for configuring a group weight.
* */
function GroupWeight({ group, config, handleInputChange }: IGroupWeightOwnProps & IGroupWeightStateProps & IGroupWeightDispatchProps) {
  const groupWeight = getGroupWeights(config)[group];
  return (
    <FormRow label={group}>
      <DisableableInput
        type="number"
        value={isNumber(groupWeight) ? groupWeight : ''}
        onChange={handleInputChange}
        min={0}
        max={100}
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
      />
    </FormRow>
  );
}

function getGroupWeights(config: ICanaryConfig): {[key: string]: number} {
  return get(config, 'classifier.groupWeights', {});
}

function mapStateToProps(state: ICanaryState, ownProps: IGroupWeightOwnProps): IGroupWeightOwnProps & IGroupWeightStateProps {
  return {
    ...ownProps,
    config: mapStateToConfig(state),
  };
}

function mapDispatchToProps(dispatch: (action: Action & any) => void, { group }: IGroupWeightOwnProps): IGroupWeightDispatchProps {
  return {
    handleInputChange: (event: React.ChangeEvent<HTMLInputElement>) => {
      dispatch(Creators.updateGroupWeight({
        group,
        weight: event.target.value ? parseInt(event.target.value, 10) : null,
      }));
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(GroupWeight);
