import { shallow } from 'enzyme';
import React from 'react';

import { RegionSelectField } from './RegionSelectField';
import { RegionSelectInput } from './RegionSelectInput';

describe('RegionSelectField', () => {
  it('propagates a selection and renders the current component value', () => {
    const component = { region: 'us-east-1' };
    const onChange = jasmine.createSpy('onChange');
    const wrapper = shallow(
      <RegionSelectField
        account="test-account"
        component={component}
        field="region"
        labelColumns={3}
        onChange={onChange}
        regions={[{ name: 'us-east-1' }, { name: 'us-west-2' }] as any}
      />,
    );

    (wrapper.find(RegionSelectInput).prop('onChange') as (event: React.ChangeEvent<HTMLSelectElement>) => void)({
      target: { value: 'us-west-2' },
    } as React.ChangeEvent<HTMLSelectElement>);

    expect(component.region).toBe('us-west-2');
    expect(onChange).toHaveBeenCalledOnceWith('us-west-2');

    wrapper.setProps({ component });
    expect(wrapper.find(RegionSelectInput).prop('value')).toBe('us-west-2');
  });
});
