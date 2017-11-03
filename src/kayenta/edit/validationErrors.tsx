import * as React from 'react';
import { connect } from 'react-redux';
import { HoverablePopover } from '@spinnaker/core';
import { ICanaryState } from '../reducers/index';

interface IConfigValidationErrorsStateProps {
  errors: string[];
}

const ConfigValidationErrors = ({ errors }: IConfigValidationErrorsStateProps) => {
  if (!errors || !errors.length) {
    return null;
  }

  const template = (
    <section>
      <p>The following errors may prevent this config from working properly:</p>
      <ul>
        {errors.map(e => (<li key={e}>{e}</li>))}
      </ul>
    </section>
  );

  return (
    <HoverablePopover placement={'left'} template={template}>
      <button className="btn btn-link">
        <i className="fa fa-exclamation-triangle"/>
      </button>
    </HoverablePopover>
  );
};


const mapStateToProps = (state: ICanaryState): IConfigValidationErrorsStateProps => ({
  errors: state.configValidationErrors,
});

export default connect(mapStateToProps)(ConfigValidationErrors);
