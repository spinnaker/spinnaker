import { render, screen } from '@testing-library/react';
import { setupUser } from '../../../utils/testUtils/userEvent';
import React from 'react';

import { ExecutionAction } from './ExecutionAction';

describe('<ExecutionAction />', () => {
  const exampleText = 'Click here';

  it('renders a link with children', () => {
    render(
      <ExecutionAction>
        <div>{exampleText}</div>
      </ExecutionAction>,
    );

    expect(screen.getByText(exampleText).closest('a')).toBeInTheDocument();
  });

  it('mouseover displays Tooltip', async () => {
    const toolTipText = 'This is a tooltip';
    const user = setupUser();
    render(<ExecutionAction tooltipText={toolTipText}>{exampleText}</ExecutionAction>);

    await user.hover(screen.getByText(exampleText));

    expect(await screen.findByText(toolTipText)).toBeVisible();
  });
});
