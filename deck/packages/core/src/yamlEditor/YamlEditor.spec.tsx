import { render, screen } from '@testing-library/react';
import React from 'react';

import { YamlEditor } from './YamlEditor';

describe('YamlEditor', () => {
  it('applies its accessible name to the editor input', () => {
    render(<YamlEditor ariaLabel="CloudFormation template YAML" onChange={vi.fn()} value="Resources: {}" />);

    expect(screen.getByRole('textbox', { name: 'CloudFormation template YAML' })).toBeInTheDocument();
  });
});
