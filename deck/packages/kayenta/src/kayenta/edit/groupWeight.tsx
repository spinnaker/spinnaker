import { get, isNumber } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';
import type { Action } from 'redux';

import * as Creators from '../actions/creators';
import { CanarySettings } from '../canary.settings';
import type { ICanaryConfig } from '../domain/ICanaryConfig';
import { DISABLE_EDIT_CONFIG, DisableableInput } from '../layout/disableable';
import FormRow from '../layout/formRow';
import type { ICanaryState } from '../reducers';
import { mapStateToConfig } from '../service/canaryConfig.service';

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
function GroupWeight({
  group,
  config,
  handleInputChange,
}: IGroupWeightOwnProps & IGroupWeightStateProps & IGroupWeightDispatchProps) {
  const groupWeight = getGroupWeights(config)[group];
  return (
    <FormRow label={group} inputOnly={true}>
      <DisableableInput
        type="number"
        value={isNumber(groupWeight) ? groupWeight : ''}
        onChange={handleInputChange}
        min={0}
        max={100}
        disabled={CanarySettings.disableConfigEdit}
        disabledStateKeys={[DISABLE_EDIT_CONFIG]}
      />
    </FormRow>
  );
}

function getGroupWeights(config: ICanaryConfig): { [key: string]: number } {
  return get(config, 'classifier.groupWeights', {});
}

function mapStateToProps(
  state: ICanaryState,
  ownProps: IGroupWeightOwnProps,
): IGroupWeightOwnProps & IGroupWeightStateProps {
  return {
    ...ownProps,
    config: mapStateToConfig(state),
  };
}

function mapDispatchToProps(
  dispatch: (action: Action & any) => void,
  { group }: IGroupWeightOwnProps,
): IGroupWeightDispatchProps {
  return {
    handleInputChange: (event: React.ChangeEvent<HTMLInputElement>) => {
      dispatch(
        Creators.updateGroupWeight({
          group,
          weight: event.target.value ? parseInt(event.target.value, 10) : null,
        }),
      );
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(GroupWeight);
