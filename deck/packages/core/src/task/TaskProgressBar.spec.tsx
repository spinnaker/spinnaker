import { render } from '@testing-library/react';
import React from 'react';

import { TaskProgressBar } from './TaskProgressBar';
import type { ITask } from '../domain';

describe('TaskProgressBar', () => {
  it('renders tasks without steps', () => {
    const task: Pick<ITask, 'id' | 'isCompleted'> = { id: 'task-1', isCompleted: true };

    const { container } = render(<TaskProgressBar task={task as ITask} />);

    expect(container.querySelector('.progress-bar')).toHaveClass('progress-bar-success');
  });
});
