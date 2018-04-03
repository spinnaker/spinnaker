import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';

import FormRow from 'kayenta/layout/formRow';
import { ICanaryState } from 'kayenta/reducers';
import * as Creators from 'kayenta/actions/creators';
import FormList from 'kayenta/layout/formList';
import { DisableableInput, DisableableTextarea, DISABLE_EDIT_CONFIG } from 'kayenta/layout/disableable';
import MetricStoreSelector from './metricStoreSelector';

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
function NameAndDescription({ name, description, changeName, changeDescription }: INameAndDescriptionDispatchProps & INameAndDescriptionStateProps) {
  return (
    <FormList>
      <FormRow label="Configuration Name">
        <DisableableInput
          type="text"
          value={name}
          onChange={changeName}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </FormRow>
      <MetricStoreSelector />
      <FormRow label="Description">
        <DisableableTextarea
          className="form-control input-sm"
          value={description}
          onChange={changeDescription}
          disabledStateKeys={[DISABLE_EDIT_CONFIG]}
        />
      </FormRow>
    </FormList>
  );
}

function mapStateToProps(state: ICanaryState) {
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

function mapDispatchToProps(dispatch: (action: Action & any) => void) {
  return {
    changeName: (event: React.ChangeEvent<HTMLInputElement>) => {
      dispatch(Creators.updateConfigName({
        name: event.target.value,
      }));
    },
    changeDescription: (event: React.ChangeEvent<HTMLTextAreaElement>) => {
      dispatch(Creators.updateConfigDescription({
        description: event.target.value,
      }));
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(NameAndDescription);
