import type { ShallowWrapper } from 'enzyme';
import { shallow } from 'enzyme';
import React from 'react';

import type { IArtifactProps } from './Artifact';
import { Artifact } from './Artifact';
import { CopyToClipboard } from '../../utils';
import type { IArtifact } from '../../domain';

const ARTIFACT_TYPE = 'docker/image';
const ARTIFACT_NAME = 'example.com/container';
const ARTIFACT_REFERENCE = 'docker.io/example.com/container:latest';

describe('<Artifact/>', () => {
  let component: ShallowWrapper<IArtifactProps>;

  const artifactNameText = () => component.find('.artifact-value').childAt(0).text();

  it("renders an artifact's name without a version when no version is provided", function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
    };

    component = shallow(<Artifact artifact={artifact} />);

    expect(artifactNameText()).toEqual(ARTIFACT_NAME);
  });

  it('renders a docker artifact as name:version', function () {
    const version = 'v001';
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
      version,
    };
    component = shallow(<Artifact artifact={artifact} />);

    expect(artifactNameText()).toEqual(`${ARTIFACT_NAME}:${version}`);
  });

  it('renders a non-docker artifact as name - version', function () {
    const version = 'v001';
    const artifact: IArtifact = {
      id: 'abcd',
      type: 'gcs/object',
      name: ARTIFACT_NAME,
      version,
    };
    component = shallow(<Artifact artifact={artifact} />);

    expect(artifactNameText()).toEqual(`${ARTIFACT_NAME} - ${version}`);
  });

  it('renders a versionless non-docker artifact without a version suffix', function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: 's3/object',
      name: 'myfile.txt',
    };
    component = shallow(<Artifact artifact={artifact} />);

    expect(artifactNameText()).toEqual('myfile.txt');
  });

  it('falls back to the reference when no name is provided', function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      reference: ARTIFACT_REFERENCE,
    };
    component = shallow(<Artifact artifact={artifact} />);

    expect(artifactNameText()).toEqual(ARTIFACT_REFERENCE);
  });

  it('adds a copy-to-clipboard button for docker artifacts', function () {
    const version = 'v001';
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
      version,
    };
    component = shallow(<Artifact artifact={artifact} />);

    const copyToClipboard = component.find(CopyToClipboard);
    expect(copyToClipboard.length).toEqual(1);
    expect(copyToClipboard.prop('text')).toEqual(`${ARTIFACT_NAME}:${version}`);
    expect(copyToClipboard.prop('toolTip')).toEqual('Copy to clipboard');
  });

  it('does not add a copy-to-clipboard button for non-docker artifacts', function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: 'gcs/object',
      name: ARTIFACT_NAME,
      version: 'v001',
    };
    component = shallow(<Artifact artifact={artifact} />);

    expect(component.find(CopyToClipboard).length).toEqual(0);
  });

  it('includes the artifact reference in the tootip', function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
      reference: ARTIFACT_REFERENCE,
    };
    component = shallow(<Artifact artifact={artifact} />);
    const dl = component.find('dl');
    expect(dl.length).toEqual(1);
    const title = dl.at(0).prop('title');
    expect(title).toMatch('Reference: ' + ARTIFACT_REFERENCE);
  });

  it('does not include a reference in the tooltip if none is specified', function () {
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
    };
    component = shallow(<Artifact artifact={artifact} />);
    const dl = component.find('dl');
    expect(dl.length).toEqual(1);
    const title = dl.at(0).prop('title');
    expect(title).not.toMatch('Reference: ');
  });
});
