import { render, screen } from '@testing-library/react';
import { setupUser } from '../../utils/testUtils/userEvent';
import React from 'react';

import { UserVerification } from './UserVerification';

import '../../presentation/main.less';

const dangerBorderColor = 'rgb(255, 0, 0)';

describe('UserVerification', () => {
  let previousDangerColor: string;

  beforeAll(() => {
    previousDangerColor = document.documentElement.style.getPropertyValue('--color-danger');
    document.documentElement.style.setProperty('--color-danger', dangerBorderColor);
  });

  afterAll(() => {
    document.documentElement.style.setProperty('--color-danger', previousDangerColor);
  });

  it('loads its verification styles', async () => {
    const host = document.createElement('div');
    document.body.appendChild(host);
    const user = setupUser();
    const { container, unmount } = render(
      <UserVerification expectedValue="production" onValidChange={() => undefined} />,
      { container: host },
    );

    const verification = container.querySelector('.user-verification') as HTMLElement;
    const verificationText = screen.getByText('production');
    const input = screen.getByRole('textbox', { name: 'Confirm production' });

    expect(window.getComputedStyle(verification).textAlign).toBe('right');
    expect(window.getComputedStyle(verificationText).fontWeight).toBe('600');
    expect(input).toHaveClass('invalid', 'highlight-pristine');
    expect(window.getComputedStyle(input).borderColor).toBe(dangerBorderColor);

    await user.type(input, 'production');

    expect(input).not.toHaveClass('invalid');

    unmount();
    host.remove();
  });
});
