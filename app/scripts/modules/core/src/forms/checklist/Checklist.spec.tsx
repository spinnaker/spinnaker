import * as React from 'react';
import { shallow } from 'enzyme';

import { noop } from 'core/utils';
import { Checklist } from './Checklist';

describe('<Checklist />', () => {
  it('initializes properly with provided values', () => {
    const checked = new Set(['a', 'b', 'c']);
    const items = new Set([ 'a', 'b', 'c', 'd' ]);
    const component = shallow(<Checklist checked={checked} items={items} onChange={noop}/>);
    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
  });

  it('updates items when an item is added externally', () => {
    const checked = new Set(['a', 'b', 'c']);
    const items = new Set([ 'a', 'b', 'c', 'd' ]);
    const component = shallow(<Checklist checked={checked} items={items} onChange={noop}/>);

    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
    items.add('e');
    component.setProps({ items });
    expect(component.find('input[type="checkbox"]').length).toBe(5);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
  });

  it('updates items when an item is removed externally', () => {
    const checked = new Set(['a', 'b', 'c']);
    const items = new Set([ 'a', 'b', 'c', 'd' ]);
    const component = shallow(<Checklist checked={checked} items={items} onChange={noop}/>);

    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
    items.delete('c');
    component.setProps({ items });
    expect(component.find('input[type="checkbox"]').length).toBe(3);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(2);
  });

  it('updates checked items when an item is checked externally', () => {
    const checked = new Set(['a', 'b', 'c']);
    const items = new Set([ 'a', 'b', 'c', 'd' ]);
    const component = shallow(<Checklist checked={checked} items={items} onChange={noop}/>);

    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
    checked.add('d');
    component.setProps({ checked });
    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(4);
  });

  it('updates checked items when an item is unchecked externally', () => {
    const checked = new Set(['a', 'b', 'c']);
    const items = new Set([ 'a', 'b', 'c', 'd' ]);
    const component = shallow(<Checklist checked={checked} items={items} onChange={noop}/>);

    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(3);
    checked.delete('c');
    component.setProps({ checked });
    expect(component.find('input[type="checkbox"]').length).toBe(4);
    expect(component.find('input[type="checkbox"][checked=true]').length).toBe(2);
  });

  it('shows the select all button when necessary', () => {
    const checked = new Set(['a', 'b', 'c']);
    const items = new Set([ 'a', 'b', 'c', 'd' ]);
    const component = shallow(<Checklist checked={checked} items={items} onChange={noop} includeSelectAllButton={true} />);
    expect(component.find('a').length).toBe(1);
  });

  it('does not show the select all button when necessary', () => {
    const checked = new Set(['a', 'b', 'c']);
    const items = new Set([ 'a', 'b', 'c', 'd' ]);
    const component = shallow(<Checklist checked={checked} items={items} onChange={noop} includeSelectAllButton={false} />);
    expect(component.find('a').length).toBe(0);
  });

  it('shows correct text for the select all button when not all the items are checked', () => {
    const checked = new Set(['a', 'b', 'c']);
    const items = new Set([ 'a', 'b', 'c', 'd' ]);
    const component = shallow(<Checklist checked={checked} items={items} onChange={noop} includeSelectAllButton={true} />);

    expect(component.find('a').text()).toBe('Select All');
  });

  it('shows correct text for the select all button when all the items are checked', () => {
    const checked = new Set(['a', 'b', 'c', 'd']);
    const items = new Set([ 'a', 'b', 'c', 'd' ]);
    const component = shallow(<Checklist checked={checked} items={items} onChange={noop} includeSelectAllButton={true} />);

    expect(component.find('a').text()).toBe('Deselect All');
  });

  it('passes an empty list to the onChange handler when deselect all clicked', () => {
    const checked = new Set(['a', 'b', 'c', 'd']);
    const items = new Set([ 'a', 'b', 'c', 'd' ]);
    const onChange = (i: Set<string>): void => { expect(i.size).toBe(0); };
    const component = shallow(<Checklist checked={checked} items={items} onChange={onChange} includeSelectAllButton={true} />);
    component.find('a').simulate('click');
  });

  it('passes a complete list to the onChange handler when select all clicked', () => {
    const checked = new Set(['a']);
    const items = new Set([ 'a', 'b', 'c', 'd' ]);
    const onChange = (i: Set<string>): void => { expect(i.size).toBe(4); };
    const component = shallow(<Checklist checked={checked} items={items} onChange={onChange} includeSelectAllButton={true} />);
    component.find('a').simulate('click');
  });
});
