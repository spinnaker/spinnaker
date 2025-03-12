import * as Creators from 'kayenta/actions/creators';
import { CanarySettings } from 'kayenta/canary.settings';
import { ICanaryConfig } from 'kayenta/domain/ICanaryConfig';
import { DISABLE_EDIT_CONFIG, DisableableInput } from 'kayenta/layout/disableable';
import { ICanaryState } from 'kayenta/reducers';
import { mapStateToConfig } from 'kayenta/service/canaryConfig.service';
import { get, isNumber } from 'lodash';
import * as React from 'react';
import { connect } from 'react-redux';
import { Action } from 'redux';

import FormRow from '../layout/formRow';

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
