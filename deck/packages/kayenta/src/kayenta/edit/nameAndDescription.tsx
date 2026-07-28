import * as React from 'react';
import { connect } from 'react-redux';
import type { Action } from 'redux';

import * as Creators from '../actions/creators';
import { CanarySettings } from '../canary.settings';
import { DISABLE_EDIT_CONFIG, DisableableInput, DisableableTextarea } from '../layout/disableable';
import FormList from '../layout/formList';
import FormRow from '../layout/formRow';
import MetricStoreSelector from './metricStoreSelector';
import type { ICanaryState } from '../reducers';

interface INameAndDescriptionDispatchProps {
  changeName: (event: React.ChangeEvent<HTMLInputElement>) => void;
  changeDescription: (event: React.ChangeEvent<HTMLTextAreaElement>) => void;
}

interface INameAndDescriptionStateProps {
  name: string;
  description: string;
}

/*
 * Configures canary config name and description.
 */
function NameAndDescription({
  name,
  description,
  changeName,
  changeDescription,
}: INameAndDescriptionDispatchProps & INameAndDescriptionStateProps) {
  return (
    <FormList>
      <FormRow label="Configuration Name" inputOnly={true}>
        <DisableableInput
          type="text"
          value={name}
          onChange={changeName}
          disabled={CanarySettings.disableConfigEdit}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </FormRow>
      <MetricStoreSelector />
      <FormRow label="Description" inputOnly={true}>
        <DisableableTextarea
          className="form-control input-sm"
          value={description}
          onChange={changeDescription}
          disabled={CanarySettings.disableConfigEdit}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </FormRow>
    </FormList>
  );
}

function mapStateToProps(state: ICanaryState): INameAndDescriptionStateProps {
  if (state.selectedConfig.config) {
    return {
      name: state.selectedConfig.config.name,
      description: state.selectedConfig.config.description,
    };
  } else {
    return {
      name: '',
      description: '',
    };
  }
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): INameAndDescriptionDispatchProps {
  return {
    changeName: (event: React.ChangeEvent<HTMLInputElement>) => {
      dispatch(
        Creators.updateConfigName({
          name: event.target.value,
        }),
      );
    },
    changeDescription: (event: React.ChangeEvent<HTMLTextAreaElement>) => {
      dispatch(
        Creators.updateConfigDescription({
          description: event.target.value,
        }),
      );
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(NameAndDescription);
