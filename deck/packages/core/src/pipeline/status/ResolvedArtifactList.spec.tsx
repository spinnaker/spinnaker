import { render, screen } from '@testing-library/react';
import React from 'react';

import { ResolvedArtifactList } from './ResolvedArtifactList';
import type { IArtifact, IExpectedArtifact } from '../../domain';

const ARTIFACT_TYPE = 'docker/image';
const ARTIFACT_NAME = 'example.com/container';

describe('<ResolvedArtifactList/>', () => {
  it('renders null when null artifacts are passed in', function () {
    const artifacts: IArtifact[] = null;
    const { container } = render(<ResolvedArtifactList artifacts={artifacts} showingExpandedArtifacts={true} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders null when 0 artifacts are passed in', function () {
    const artifacts: IArtifact[] = [];
    const resolvedExpectedArtifacts = artifacts.map((a) => ({ boundArtifact: a } as IExpectedArtifact));
    const { container } = render(
      <ResolvedArtifactList
        artifacts={artifacts}
        resolvedExpectedArtifacts={resolvedExpectedArtifacts}
        showingExpandedArtifacts={true}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders null when artifacts are set to not expanded', () => {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];
    const resolvedExpectedArtifacts = artifacts.map((a) => ({ boundArtifact: a } as IExpectedArtifact));
    const { container } = render(
      <ResolvedArtifactList
        artifacts={artifacts}
        resolvedExpectedArtifacts={resolvedExpectedArtifacts}
        showingExpandedArtifacts={false}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders two columns when columnLayoutAfter is set to 2', function () {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
      {
        id: 'efgh',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];

    const resolvedExpectedArtifacts = artifacts.map((a) => ({ boundArtifact: a } as IExpectedArtifact));
    const { container } = render(
      <ResolvedArtifactList
        artifacts={artifacts}
        resolvedExpectedArtifacts={resolvedExpectedArtifacts}
        showingExpandedArtifacts={true}
      />,
    );

    expect(container.querySelectorAll('.artifact-list-column')).toHaveLength(2);
    expect(screen.getAllByTitle(`Name: ${ARTIFACT_NAME} Type: ${ARTIFACT_TYPE}`)).toHaveLength(2);
  });

  it('does not render an artifact without a type and name', function () {
    const singleArtifact: IArtifact[] = [
      {
        id: 'abcd',
      },
    ];
    const resolvedExpectedArtifacts = singleArtifact.map((a) => ({ boundArtifact: a } as IExpectedArtifact));
    const { container } = render(
      <ResolvedArtifactList
        artifacts={singleArtifact}
        resolvedExpectedArtifacts={resolvedExpectedArtifacts}
        showingExpandedArtifacts={true}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('only renders an artifacts that has a type and name', function () {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
      },
      {
        id: 'abcd2',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];
    const resolvedExpectedArtifacts = artifacts.map((a) => ({ boundArtifact: a } as IExpectedArtifact));
    render(
      <ResolvedArtifactList
        artifacts={artifacts}
        resolvedExpectedArtifacts={resolvedExpectedArtifacts}
        showingExpandedArtifacts={true}
      />,
    );
    expect(screen.getAllByTitle(`Name: ${ARTIFACT_NAME} Type: ${ARTIFACT_TYPE}`)).toHaveLength(1);
  });

  it('does not render artifacts for which there is no expected artifact in the pipeline', function () {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];
    render(<ResolvedArtifactList artifacts={artifacts} showingExpandedArtifacts={true} />);
    expect(screen.getByText(/1.*artifact.*not.*consumed/)).toBeVisible();
  });
});
