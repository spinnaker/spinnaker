import { render, screen } from '@testing-library/react';
import React from 'react';

import type { ITag } from './Tag';
import { TagList } from './TagList';

describe('<TagList/>', () => {
  it('displays a tag list', () => {
    const tags: ITag[] = [1, 2, 3].map((seed) => ({ key: 'key', text: `some_text${seed}` }));

    const { container } = render(<TagList tags={tags} />);

    expect(container.firstElementChild).toHaveClass('tag-list');
    expect(screen.getAllByText('KEY')).toHaveLength(3);
    tags.forEach(({ text }) => expect(screen.getByText(text)).toBeInTheDocument());
  });
});
