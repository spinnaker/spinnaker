import React from 'react';
import { mount, ReactWrapper } from 'enzyme';

import { Key } from '../Keys';
import { DeleteType, ITag, ITagProps, Tag } from './Tag';

describe('<Tag/>', () => {
  let component: ReactWrapper<ITagProps, any>;

  function getNewTag(): ITag {
    return {
      key: 'key',
      text: 'some_text',
    };
  }

  function getNewTagComponent(
    tag: ITag,
    onDelete: (t: ITag, deleteType: DeleteType) => void,
    onKeyUp: (t: ITag, key: Key) => void,
  ): ReactWrapper<ITagProps, any> {
    return mount(<Tag tag={tag} onDelete={onDelete} onKeyUp={onKeyUp} />);
  }

  it('should display a tag', () => {
    const tag = getNewTag();
    component = getNewTagComponent(tag, undefined, undefined);

    expect(component.find('div.tag__category').text()).toBe(tag.key.toLocaleUpperCase());
    expect(component.find('div.tag__label').text()).toBe(tag.text);
  });

  describe('tag callback event handling', () => {
    let tag: ITag;
    beforeEach(() => {
      tag = getNewTag();
    });

    describe('handle key up event', () => {
      it('should call the keyUp handler with the left arrow key', (done: Function) => {
        function handleKeyUp(t: ITag, key: Key): void {
          expect(t.key).toBe(tag.key);
          expect(t.text).toBe(tag.text);
          expect(key).toBe(Key.LEFT_ARROW);
          done();
        }
        component = getNewTagComponent(tag, undefined, handleKeyUp);
        component.simulate('keyup', { key: Key.LEFT_ARROW });
      });

      it('should call the keyUp handler with the right arrow key', (done: Function) => {
        function handleKeyUp(t: ITag, key: Key): void {
          expect(t.key).toBe(tag.key);
          expect(t.text).toBe(tag.text);
          expect(key).toBe(Key.RIGHT_ARROW);
          done();
        }
        component = getNewTagComponent(tag, undefined, handleKeyUp);
        component.simulate('keyup', { key: Key.RIGHT_ARROW });
      });
    });

    describe('handle remove click event', () => {
      it('should call the delete handler with the deletion type of backspace when the backspace key is pressed', (done: Function) => {
        function handleDelete(t: ITag, deleteType: DeleteType) {
          expect(t.key).toBe(tag.key);
          expect(t.text).toBe(tag.text);
          expect(deleteType).toBe(DeleteType.BACKSPACE);
          done();
        }
        component = getNewTagComponent(tag, handleDelete, undefined);
        component.simulate('keyup', { key: Key.BACKSPACE });
      });

      it('should call the delete handler with the deletion type of backspace when the delete key is pressed', (done: Function) => {
        function handleDelete(t: ITag, deleteType: DeleteType) {
          expect(t.key).toBe(tag.key);
          expect(t.text).toBe(tag.text);
          expect(deleteType).toBe(DeleteType.BACKSPACE);
          done();
        }
        component = getNewTagComponent(tag, handleDelete, undefined);
        component.simulate('keyup', { key: Key.DELETE });
      });
    });
  });
});
