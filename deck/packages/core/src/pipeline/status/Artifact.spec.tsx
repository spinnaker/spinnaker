import { render, screen } from '@testing-library/react';
import React from 'react';

import { Artifact } from './Artifact';
import type { IArtifact } from '../../domain';
import { setupUser } from '../../utils/testUtils';

const ARTIFACT_TYPE = 'docker/image';
const ARTIFACT_NAME = 'example.com/container';
const ARTIFACT_REFERENCE = 'docker.io/example.com/container:latest';

describe('<Artifact/>', () => {
  it("renders an artifact's name without a version when no version is provided", function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
    };

    render(<Artifact artifact={artifact} />);

    expect(screen.getByText(ARTIFACT_NAME, { selector: 'span' })).toBeVisible();
  });

  it('renders a docker artifact as name:version', function () {
    const version = 'v001';
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
      version,
    };
    render(<Artifact artifact={artifact} />);

    expect(screen.getByText(`${ARTIFACT_NAME}:${version}`, { selector: 'span' })).toBeVisible();
  });

  it('renders a non-docker artifact as name - version', function () {
    const version = 'v001';
    const artifact: IArtifact = {
      id: 'abcd',
      type: 'gcs/object',
      name: ARTIFACT_NAME,
      version,
    };
    render(<Artifact artifact={artifact} />);

    expect(screen.getByText(`${ARTIFACT_NAME} - ${version}`, { selector: 'span' })).toBeVisible();
  });

  it('renders a versionless non-docker artifact without a version suffix', function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: 's3/object',
      name: 'myfile.txt',
    };
    render(<Artifact artifact={artifact} />);

    expect(screen.getByText('myfile.txt', { selector: 'span' })).toBeVisible();
  });

  it('falls back to the reference when no name is provided', function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      reference: ARTIFACT_REFERENCE,
    };
    render(<Artifact artifact={artifact} />);

    expect(screen.getByText(ARTIFACT_REFERENCE, { selector: 'span' })).toBeVisible();
  });

  it('adds a copy-to-clipboard button for docker artifacts', async function () {
    const user = setupUser();
    const version = 'v001';
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
      version,
    };
    render(<Artifact artifact={artifact} />);

    const copyButton = screen.getByRole('button', { name: 'Copy to clipboard' });
    expect(screen.getAllByRole('button', { name: 'Copy to clipboard' })).toHaveLength(1);
    expect(screen.getByDisplayValue(`${ARTIFACT_NAME}:${version}`)).toBeInTheDocument();

    await user.hover(copyButton);

    expect(await screen.findByRole('tooltip')).toHaveTextContent('Copy to clipboard');
  });

  it('does not add a copy-to-clipboard button for non-docker artifacts', function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: 'gcs/object',
      name: ARTIFACT_NAME,
      version: 'v001',
    };
    render(<Artifact artifact={artifact} />);

    expect(screen.queryByRole('button', { name: 'Copy to clipboard' })).not.toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('includes the artifact reference in the tootip', function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
      reference: ARTIFACT_REFERENCE,
    };
    const { container } = render(<Artifact artifact={artifact} />);
    expect(container.querySelectorAll('dl')).toHaveLength(1);
    expect(container.querySelector('dl')).toHaveAttribute(
      'title',
      expect.stringContaining(`Reference: ${ARTIFACT_REFERENCE}`),
    );
  });

  it('does not include a reference in the tooltip if none is specified', function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
    };
    const { container } = render(<Artifact artifact={artifact} />);
    expect(container.querySelectorAll('dl')).toHaveLength(1);
    expect(container.querySelector('dl')).not.toHaveAttribute('title', expect.stringContaining('Reference: '));
  });
});
