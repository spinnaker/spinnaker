import * as React from 'react';
import { connect } from 'react-redux';
import { Action } from 'redux';
import { get, isNumber } from 'lodash';

import FormRow from '../layout/formRow';
import { ICanaryState } from '../reducers/index';
import { ICanaryConfig } from '../domain/ICanaryConfig';
import { UPDATE_GROUP_WEIGHT } from '../actions/index';
import { mapStateToConfig } from '../service/canaryConfig.service';

interface IGroupWeightOwnProps {
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
  const groupWeight = getGroupWeights(config)[group] || 0;
  return (
    <FormRow label={group}>
      <input
        type="number"
        className="input-sm form-control"
        value={isNumber(groupWeight) ? groupWeight : ''}
        onChange={handleInputChange}
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
      dispatch({
        type: UPDATE_GROUP_WEIGHT,
        group,
        weight: event.target.value ? parseInt(event.target.value, 10) : null,
      });
    }
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(GroupWeight);
