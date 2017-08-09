import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';

import FormRow from '../layout/formRow';
import JudgeSelect from './judgeSelect';
import { ICanaryState } from '../reducers/index';
import {
  UPDATE_CONFIG_DESCRIPTION,
  UPDATE_CONFIG_NAME
} from '../actions/index';

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
    <form role="form" className="form-horizontal container-fluid">
      <FormRow label="Configuration Name">
        <input
          type="text"
          className="form-control"
          value={name}
          onChange={changeName}
        />
      </FormRow>
      <FormRow label="Description">
        <textarea
          className="form-control"
          value={description}
          onChange={changeDescription}
        />
      </FormRow>
      {/* TODO: either rename the NameAndDescription component (and label), or find a different place for the judge selector. */}
      <FormRow label="Judge">
        <JudgeSelect/>
      </FormRow>
    </form>
  );
}

function mapStateToProps(state: ICanaryState) {
  if (state.selectedConfig) {
    return {
      name: state.selectedConfig.name,
      description: state.selectedConfig.description,
    };
  } else {
    return {
      name: '',
      description: ''
    };
  }
}

function mapDispatchToProps(dispatch: (action: Action & any) => void) {
  return {
    changeName: (event: React.ChangeEvent<HTMLInputElement>) => {
      dispatch({
        type: UPDATE_CONFIG_NAME,
        name: event.target.value,
      });
    },
    changeDescription: (event: React.ChangeEvent<HTMLTextAreaElement>) => {
      dispatch({
        type: UPDATE_CONFIG_DESCRIPTION,
        description: event.target.value,
      });
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(NameAndDescription);
