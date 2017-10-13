import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';

import FormRow from '../layout/formRow';
import { ICanaryState } from '../reducers/index';
import * as Creators from '../actions/creators';
import FormList from '../layout/formList';
import KayentaInput from '../layout/kayentaInput';

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
        <KayentaInput
          type="text"
          value={name}
          onChange={changeName}
        />
      </FormRow>
      <FormRow label="Description">
        <textarea
          className="form-control input-sm"
          value={description}
          onChange={changeDescription}
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
