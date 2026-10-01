import { render, screen } from '@testing-library/react';
import React from 'react';

import { ArtifactList } from './ArtifactList';
import type { IArtifact } from '../../domain';

const ARTIFACT_TYPE = 'docker/image';
const ARTIFACT_NAME = 'example.com/container';

describe('<ArtifactList/>', () => {
  it('renders null when null artifacts are passed in', function () {
    const artifacts: IArtifact[] = null;
    const { container } = render(<ArtifactList artifacts={artifacts} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders null when 0 artifacts are passed in', function () {
    const artifacts: IArtifact[] = [];
    const { container } = render(<ArtifactList artifacts={artifacts} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a list when artifacts are passed in', function () {
    const artifacts: IArtifact[] = [
      {
        id: 'abcd',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
      {
        id: 'defg',
        type: ARTIFACT_TYPE,
        name: ARTIFACT_NAME,
      },
    ];
    render(<ArtifactList artifacts={artifacts} />);
    expect(screen.getAllByTitle(`Name: ${ARTIFACT_NAME} Type: ${ARTIFACT_TYPE}`)).toHaveLength(2);
  });
});
