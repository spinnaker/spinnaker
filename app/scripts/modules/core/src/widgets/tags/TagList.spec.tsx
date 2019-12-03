import React from 'react';
import { ReactWrapper, mount } from 'enzyme';

import { ITag } from './Tag';
import { ITagListProps, TagList } from './TagList';

describe('<TagList/>', () => {
  let component: ReactWrapper<ITagListProps, any>;

  function getNewTag(seed: number): ITag {
    return {
      key: 'key',
      text: `some_text${seed}`,
    };
  }

  function getNewTagList(): ReactWrapper<ITagListProps, any> {
    return mount(<TagList tags={[1, 2, 3].map((seed: number) => getNewTag(seed))} />);
  }

  it('should display a tag list', () => {
    component = getNewTagList();
    expect(component.render().hasClass('tag-list')).toBeTruthy();
    expect(component.find('div.tag').length).toBe(3);
  });
});
