import * as React from 'react';
import { ShallowWrapper, shallow } from 'enzyme';
import { mock } from 'angular';
import { REACT_MODULE } from 'core/reactShims';

import { IArtifact } from 'core/domain';

import { Artifact, IArtifactProps } from './Artifact';

const ARTIFACT_TYPE = 'docker/image';
const ARTIFACT_NAME = 'example.com/container';
const ARTIFACT_REFERENCE = 'docker.io/example.com/container:latest';

describe('<Artifact/>', () => {
  let component: ShallowWrapper<IArtifactProps>;

  beforeEach(mock.module(REACT_MODULE));
  beforeEach(mock.inject(() => {})); // Angular is lazy.

  it("renders an artifact's name", function() {
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
    };
    component = shallow(<Artifact artifact={artifact} />);
    const dl = component.find('dl');
    const dt = dl.find('dt');
    const dd = dl.find('dd');
    expect(dl.length).toEqual(1);
    expect(dt.length).toEqual(1);
    expect(dd.length).toEqual(1);
    expect(dd.at(0).text()).toEqual(ARTIFACT_NAME);
  });

  it('renders an artifact version if present', function() {
    const version = 'v001';
    const artifact: IArtifact = {
      id: 'abcd',
      type: ARTIFACT_TYPE,
      name: ARTIFACT_NAME,
      version,
    };
    component = shallow(<Artifact artifact={artifact} />);
    const dl = component.find('dl');
    const dt = dl.find('dt');
    const dd = dl.find('dd');
    expect(dl.length).toEqual(1);
    expect(dt.length).toEqual(2);
    expect(dd.length).toEqual(2);
    expect(dd.at(1).text()).toEqual(version);
  });

  it('includes the artifact reference in the tootip', function() {
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

  it('does not include a reference in the tooltip if none is specified', function() {
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
