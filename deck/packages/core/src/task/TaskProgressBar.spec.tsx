import { shallow } from 'enzyme';
import React from 'react';

import { TaskProgressBar } from './TaskProgressBar';
import type { ITask } from '../domain';

describe('TaskProgressBar', () => {
  it('renders tasks without steps', () => {
    const task = ({ id: 'task-1', isCompleted: true } as unknown) as ITask;

    const wrapper = shallow(<TaskProgressBar task={task} />);

    expect(wrapper.find('.progress-bar-success').exists()).toBe(true);
  });
});
