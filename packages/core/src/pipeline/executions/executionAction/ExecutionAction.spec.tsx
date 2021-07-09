import React from 'react';
import { shallow, mount } from 'enzyme';

import { ExecutionAction } from './ExecutionAction';

describe('<ExecutionAction />', () => {
  const exampleText = 'Click here';

  it('renders a link with children', () => {
    const wrapper = shallow(
      <ExecutionAction>
        <div>{exampleText}</div>
      </ExecutionAction>,
    );
    const aTag = wrapper.find('a');
    expect(aTag.text()).toEqual(exampleText);
  });

  it('mouseover displayes Tooltip', () => {
    const toolTipText = 'This is a tooltip';
    const wrapper = mount(<ExecutionAction tooltipText={toolTipText}>{exampleText}</ExecutionAction>);
    const aTag = wrapper.find('a');
    aTag.simulate('mouseOver');

    const toolTip = document.getElementById(toolTipText);
    expect(toolTip.innerText).toEqual(toolTipText);
  });
});
