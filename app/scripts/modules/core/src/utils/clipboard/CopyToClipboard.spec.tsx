import { mount } from 'enzyme';
import React from 'react';
import { logger } from '../Logger';
import { CopyToClipboard } from './CopyToClipboard';

describe('<CopyToClipboard />', () => {
  beforeEach(() => spyOn(logger, 'log'));

  it('renders an input with the text value', () => {
    const wrapper = mount(<CopyToClipboard toolTip="Copy Rebel Girl" text="Rebel Girl" />);
    const input = wrapper.find('textarea');
    expect(input.get(0).props.value).toEqual('Rebel Girl');
  });

  it('Mouseover/click triggers overlay with toolTip', () => {
    const wrapper = mount(<CopyToClipboard toolTip="Copy Rebel Girl" text="Rebel Girl" />);
    const button = wrapper.find('button');
    button.simulate('mouseOver');

    // Grab the overlay from document by generated ID
    const overlay = document.getElementById('clipboardValue-Rebel-Girl');
    expect(overlay.innerText).toEqual('Copy Rebel Girl');
  });

  it('Shows tooltip when button clicked, even if no default tooltip configured', () => {
    const wrapper = mount(<CopyToClipboard text="No Tooltip" />);
    const button = wrapper.find('button');
    button.simulate('mouseOver');

    // Grab the overlay from document by generated ID
    let overlay = document.getElementById('clipboardValue-No-Tooltip');
    expect(overlay).toBeFalsy();

    button.simulate('click');
    overlay = document.getElementById('clipboardValue-No-Tooltip');
    expect(overlay.innerText).toEqual('Copied!');
  });

  it('fires a GA event on click', () => {
    const wrapper = mount(<CopyToClipboard toolTip="Copy Rebel Girl" text="Rebel Girl" />);
    const button = wrapper.find('button');
    button.simulate('click');
    expect(logger.log).toHaveBeenCalled();
  });
});
