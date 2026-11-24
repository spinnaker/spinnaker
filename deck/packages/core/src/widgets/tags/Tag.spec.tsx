import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import { Key } from '../Keys';
import type { ITag } from './Tag';
import { DeleteType, Tag } from './Tag';

const tag: ITag = { key: 'key', text: 'some_text' };

describe('<Tag/>', () => {
  it('displays a tag', () => {
    render(<Tag tag={tag} />);

    expect(screen.getByText('KEY')).toBeInTheDocument();
    expect(screen.getByText('some_text')).toBeInTheDocument();
  });

  it.each([Key.LEFT_ARROW, Key.RIGHT_ARROW])('calls the keyUp handler with %s', (key) => {
    const onKeyUp = vi.fn();
    render(<Tag tag={tag} onKeyUp={onKeyUp} />);

    fireEvent.keyUp(screen.getByText('some_text').closest('.tag'), { key });

    expect(onKeyUp).toHaveBeenCalledWith(tag, key);
  });

  it.each([Key.BACKSPACE, Key.DELETE])('calls the delete handler with backspace deletion for %s', (key) => {
    const onDelete = vi.fn();
    render(<Tag tag={tag} onDelete={onDelete} />);

    fireEvent.keyUp(screen.getByText('some_text').closest('.tag'), { key });

    expect(onDelete).toHaveBeenCalledWith(tag, DeleteType.BACKSPACE);
  });

  it('removes the tag through a named, keyboard-focusable button', async () => {
    const onDelete = vi.fn();
    render(<Tag tag={tag} onDelete={onDelete} />);

    const remove = screen.getByRole('button', { name: 'Remove some_text tag' });
    remove.focus();
    expect(remove).toHaveFocus();
    await userEvent.click(remove);

    expect(onDelete).toHaveBeenCalledTimes(1);
    expect(onDelete).toHaveBeenCalledWith(tag, DeleteType.REMOVE);
  });
});
