import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';

import FormRow from '../layout/formRow';
import JudgeSelect, { JudgeSelectRenderState } from './judgeSelect';
import ScoreThresholds from './scoreThresholds';
import { ICanaryState } from '../reducers/index';
import * as Creators from '../actions/creators';
import FormList from '../layout/formList';

interface INameAndDescriptionDispatchProps {
  changeName: (event: React.ChangeEvent<HTMLInputElement>) => void;
  changeDescription: (event: React.ChangeEvent<HTMLTextAreaElement>) => void;
}

interface INameAndDescriptionStateProps {
  name: string;
  description: string;
  judgeSelectRenderState: JudgeSelectRenderState;
}

/*
 * Configures canary config name and description.
 */
function NameAndDescription({ name, description, changeName, changeDescription, judgeSelectRenderState }: INameAndDescriptionDispatchProps & INameAndDescriptionStateProps) {
  return (
    <FormList>
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
      {judgeSelectRenderState !== JudgeSelectRenderState.None && <JudgeSelect/>}
      <ScoreThresholds/>
    </FormList>
  );
}

function mapStateToProps(state: ICanaryState) {
  if (state.selectedConfig.config) {
    return {
      name: state.selectedConfig.config.name,
      description: state.selectedConfig.config.description,
      judgeSelectRenderState: state.selectedConfig.judge.renderState,
    };
  } else {
    return {
      name: '',
      description: '',
      judgeSelectRenderState: state.selectedConfig.judge.renderState,
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
