import * as React from 'react';
import { mount } from 'enzyme';
import { Creatable, Option } from 'react-select';
import { $q } from 'ngimport';
import Spy = jasmine.Spy;

import { AccountService, noop, NgReact } from 'core';

import { ManifestKindSearchService } from 'kubernetes/v2/manifest/ManifestKindSearch';
import { ManifestSelector } from 'kubernetes/v2/manifest/selector/ManifestSelector';

describe('<ManifestSelector />', () => {
  let searchService: Spy;

  beforeEach(() => {
    searchService = spyOn(ManifestKindSearchService, 'search').and.returnValue($q.resolve([]));
    spyOn(AccountService, 'getAllAccountDetailsForProvider').and.returnValue($q.resolve([]));
  });

  describe('initialization', () => {
    it('renders namespace from input props', () => {
      const wrapper = component({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      const namespace = wrapper
        .find({ label: 'Namespace' })
        .find(Creatable)
        .first();
      expect((namespace.props().value as Option).value).toEqual('default');
    });

    it('renders kind from input props', () => {
      const wrapper = component({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      const kind = wrapper
        .find({ label: 'Kind' })
        .find(Creatable)
        .first();
      expect((kind.props().value as Option).value).toEqual('configMap');
    });

    it('renders name from input props', () => {
      const wrapper = component({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      const name = wrapper
        .find({ label: 'Name' })
        .find(Creatable)
        .first();
      expect((name.props().value as Option).value).toEqual('my-config-map');
    });
  });

  describe('change handlers', () => {
    it('calls the search service after updating the `Kind` field', () => {
      const wrapper = component({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      const kind = wrapper
        .find({ label: 'Kind' })
        .find(Creatable)
        .first();
      kind.props().onChange({ value: 'deployment', label: 'deployment' });
      expect(searchService).toHaveBeenCalledWith('deployment', 'default', 'my-account');
    });

    it('calls the search service after updating the `Namespace` field', () => {
      const wrapper = component({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      const namespace = wrapper
        .find({ label: 'Namespace' })
        .find(Creatable)
        .first();
      namespace.props().onChange({ value: 'kube-system', label: 'kube-system' });
      expect(searchService).toHaveBeenCalledWith('configMap', 'kube-system', 'my-account');
    });

    it('clears namespace when changing account if account does not have selected namespace', () => {
      const wrapper = component({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });
      wrapper.setState({
        accounts: [
          { name: 'my-account', namespaces: ['default'] },
          { name: 'my-other-account', namespaces: ['other-default'] },
        ],
      });

      const account = wrapper.find(NgReact.AccountSelectField).first();
      account.props().onChange('my-other-account');
      expect(wrapper.instance().state.selector.location).toBeFalsy();
    });
  });
});

const component = (selector: any) => mount(<ManifestSelector onChange={noop} selector={selector} /> as any);
