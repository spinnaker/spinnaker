import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';

import * as ArtifactExports from '..';
import { ExpectedArtifactSelector } from './ExpectedArtifactSelector';
import type { IExpectedArtifact } from '../../domain';

const artifact = (type: string): IExpectedArtifact => {
  const expectedArtifact = ArtifactExports.ExpectedArtifactService.createEmptyArtifact();
  expectedArtifact.matchArtifact.customKind = false;
  expectedArtifact.matchArtifact.type = type;
  expectedArtifact.displayName = type;
  return expectedArtifact;
};

function openOptions() {
  fireEvent.mouseDown(screen.getByRole('combobox'));
}

describe('<ExpectedArtifactSelector/>', () => {
  it('returns the exact artifact selected by the user', async () => {
    const gcsArtifact = artifact('gcs/object');
    const dockerArtifact = artifact('docker/image');
    const onChange = vi.fn();
    render(<ExpectedArtifactSelector expectedArtifacts={[gcsArtifact, dockerArtifact]} onChange={onChange} />);

    openOptions();
    await userEvent.click(screen.getByText('docker/image'));

    expect(onChange).toHaveBeenCalledTimes(1);
    expect(onChange).toHaveBeenCalledWith(dockerArtifact);
  });

  it('offers only artifact types matching an offered pattern', () => {
    render(
      <ExpectedArtifactSelector
        expectedArtifacts={[artifact('gcs/object'), artifact('docker/image')]}
        onChange={() => {}}
        offeredArtifactTypes={[/.*gcs.*/]}
      />,
    );

    openOptions();

    expect(screen.getByText('gcs/object')).toBeInTheDocument();
    expect(screen.queryByText('docker/image')).not.toBeInTheDocument();
  });

  it('offers artifact types matching any offered pattern', () => {
    render(
      <ExpectedArtifactSelector
        expectedArtifacts={[artifact('gcs/object'), artifact('foo/bar'), artifact('docker/image')]}
        onChange={() => {}}
        offeredArtifactTypes={[/.*gcs.*/, /.*docker.*/]}
      />,
    );

    openOptions();

    expect(screen.getByText('gcs/object')).toBeInTheDocument();
    expect(screen.getByText('docker/image')).toBeInTheDocument();
    expect(screen.queryByText('foo/bar')).not.toBeInTheDocument();
  });

  it('excludes artifact types matching an excluded pattern', () => {
    render(
      <ExpectedArtifactSelector
        expectedArtifacts={[artifact('gcs/object'), artifact('gcs/bucket'), artifact('docker/image')]}
        onChange={() => {}}
        excludedArtifactTypes={[/.*gcs.*/]}
      />,
    );

    openOptions();

    expect(screen.getByText('docker/image')).toBeInTheDocument();
    expect(screen.queryByText('gcs/object')).not.toBeInTheDocument();
    expect(screen.queryByText('gcs/bucket')).not.toBeInTheDocument();
  });

  it('excludes artifact types matching any excluded pattern', () => {
    render(
      <ExpectedArtifactSelector
        expectedArtifacts={[artifact('gcs/object'), artifact('docker/image')]}
        onChange={() => {}}
        excludedArtifactTypes={[/.*gcs.*/, /.*docker.*/]}
      />,
    );

    openOptions();

    expect(screen.queryByText('gcs/object')).not.toBeInTheDocument();
    expect(screen.queryByText('docker/image')).not.toBeInTheDocument();
  });

  it('offers creation only when a creation callback is provided', async () => {
    const onChange = vi.fn();
    const onRequestCreate = vi.fn();
    const { rerender } = render(
      <ExpectedArtifactSelector expectedArtifacts={[artifact('gcs/object')]} onChange={onChange} />,
    );
    openOptions();
    expect(screen.queryByText('Create new...')).not.toBeInTheDocument();

    rerender(
      <ExpectedArtifactSelector
        expectedArtifacts={[artifact('gcs/object')]}
        onChange={onChange}
        onRequestCreate={onRequestCreate}
      />,
    );
    openOptions();
    await userEvent.click(screen.getByText('Create new...'));
    expect(onRequestCreate).toHaveBeenCalledTimes(1);
    expect(onRequestCreate).toHaveBeenCalledWith();
    expect(onChange).not.toHaveBeenCalled();
  });
});
