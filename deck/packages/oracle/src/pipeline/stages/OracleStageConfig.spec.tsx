import { mount } from 'enzyme';
import React from 'react';

import { OracleStageConfig } from './OracleStageConfig';

describe('<OracleStageConfig />', () => {
  const createProps = (stage: any = {}) => ({
    stage,
    updateStageField: jasmine.createSpy('updateStageField'),
  });

  it('initializes Oracle stage defaults through updateStageField', () => {
    const props = createProps();

    mount(<OracleStageConfig {...props} />);

    expect(props.stage).toEqual({});
    expect(props.updateStageField).toHaveBeenCalledWith({
      cloudProvider: 'oracle',
      cloudProviderType: 'oracle',
      regions: [],
    });
  });

  it('updates user input through updateStageField', () => {
    const props = createProps({ cloudProvider: 'oracle', cloudProviderType: 'oracle', regions: ['us-phoenix-1'] });
    const component = mount(<OracleStageConfig {...props} />);

    component
      .find('input')
      .at(0)
      .simulate('change', { target: { value: 'oracle-account' } });
    component
      .find('input')
      .at(1)
      .simulate('change', { target: { value: 'us-ashburn-1' } });

    expect(props.updateStageField).toHaveBeenCalledWith({ credentials: 'oracle-account' });
    expect(props.updateStageField).toHaveBeenCalledWith({ regions: ['us-ashburn-1'] });
  });
});
