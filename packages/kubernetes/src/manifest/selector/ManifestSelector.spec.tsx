import React from 'react';
import { mount } from 'enzyme';
import Select, { Creatable, Option } from 'react-select';
import { $q } from 'ngimport';
import Spy = jasmine.Spy;

import {
  AccountSelectInput,
  AccountService,
  ScopeClusterSelector,
  createFakeReactSyntheticEvent,
  noop,
} from '@spinnaker/core';

import { ManifestKindSearchService } from '../ManifestKindSearch';
import { ManifestSelector, IManifestSelectorState } from './ManifestSelector';
import { SelectorMode } from './IManifestSelector';
import LabelEditor from './labelEditor/LabelEditor';
import { IManifestLabelSelector } from './IManifestLabelSelector';

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

      const namespace = wrapper.find({ label: 'Namespace' }).find(Creatable).first();
      expect((namespace.props().value as Option).value).toEqual('default');
    });

    it('renders kind from input props', () => {
      const wrapper = component({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      const kind = wrapper.find({ label: 'Kind' }).find(Creatable).first();
      expect((kind.props().value as Option).value).toEqual('configMap');
    });

    it('renders name from input props', () => {
      const wrapper = component({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      const name = wrapper.find({ label: 'Name' }).find(Creatable).first();
      expect((name.props().value as Option).value).toEqual('my-config-map');
    });

    it('renders kinds from input props', () => {
      const wrapper = component({
        account: 'my-account',
        kinds: ['configMap', 'deployment'],
        location: 'default',
        mode: SelectorMode.Label,
      });

      const kinds = wrapper.find({ label: 'Kinds' }).find(Select).first();
      expect(kinds.props().value).toEqual(['configMap', 'deployment']);
    });

    it('renders labels from input props', () => {
      const labelSelectors: IManifestLabelSelector[] = [
        {
          key: 'label-key',
          kind: 'EQUALS',
          values: ['label-value'],
        },
      ];
      const wrapper = component({
        account: 'my-account',
        kinds: ['configMap', 'deployment'],
        labelSelectors: {
          selectors: labelSelectors,
        },
        location: 'default',
        mode: SelectorMode.Label,
      });

      const labelEditor = wrapper.find(LabelEditor);
      expect(labelEditor.prop('labelSelectors')).toEqual(labelSelectors);
    });

    describe('cluster dropdown', () => {
      const buildPropsWithApplicationData = (data: any[]) => ({
        modes: [SelectorMode.Static, SelectorMode.Dynamic],
        application: { getDataSource: () => ({ data }) },
      });

      it("includes cluster if selected kind matches the cluster's server groups' kind", () => {
        const wrapper = component(
          {
            kind: 'replicaSet',
            account: 'my-account',
            location: 'default',
            mode: SelectorMode.Dynamic,
          },
          buildPropsWithApplicationData([
            {
              name: 'replicaSet my-replica-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'replicaSet my-replica-set',
            },
          ]),
        );

        const cluster = wrapper.find({ label: 'Cluster' }).find(ScopeClusterSelector).first();
        expect(cluster.props().clusters).toEqual(['replicaSet my-replica-set']);
      });

      it("does not include cluster if selected kind does not match cluster's server groups' kind", () => {
        const wrapper = component(
          {
            kind: 'statefulSet',
            account: 'my-account',
            location: 'default',
            mode: SelectorMode.Dynamic,
          },
          buildPropsWithApplicationData([
            {
              name: 'replicaSet my-replica-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'replicaSet my-replica-set',
            },
          ]),
        );

        const cluster = wrapper.find({ label: 'Cluster' }).find(ScopeClusterSelector).first();
        expect(cluster.props().clusters).toEqual([]);
      });

      it('handles case in which a cluster has two different kinds of server groups', () => {
        const wrapper = component(
          {
            kind: 'statefulSet',
            account: 'my-account',
            location: 'default',
            mode: SelectorMode.Dynamic,
          },
          buildPropsWithApplicationData([
            {
              name: 'replicaSet my-replica-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'my-cluster',
            },
            {
              name: 'statefulSet my-stateful-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'my-cluster',
            },
          ]),
        );

        const cluster = wrapper.find({ label: 'Cluster' }).find(ScopeClusterSelector).first();
        expect(cluster.props().clusters).toEqual(['my-cluster']);
      });

      it("does not include cluster if the cluster's server groups are managed", () => {
        const wrapper = component(
          {
            kind: 'replicaSet',
            account: 'my-account',
            location: 'default',
            mode: SelectorMode.Dynamic,
          },
          buildPropsWithApplicationData([
            {
              name: 'replicaSet my-replica-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'my-cluster',
              serverGroupManagers: ['deployment my-deployment'],
            },
          ]),
        );

        const cluster = wrapper.find({ label: 'Cluster' }).find(ScopeClusterSelector).first();
        expect(cluster.props().clusters).toEqual([]);
      });

      it('filters clusters by account', () => {
        const wrapper = component(
          {
            kind: 'replicaSet',
            account: 'my-other-account',
            location: 'default',
            mode: SelectorMode.Dynamic,
          },
          buildPropsWithApplicationData([
            {
              name: 'replicaSet my-replica-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'my-cluster',
            },
          ]),
        );

        const cluster = wrapper.find({ label: 'Cluster' }).find(ScopeClusterSelector).first();
        expect(cluster.props().clusters).toEqual([]);
      });

      it('filters clusters by namespace', () => {
        const wrapper = component(
          {
            kind: 'replicaSet',
            account: 'my-account',
            location: 'my-other-namespace',
            mode: SelectorMode.Dynamic,
          },
          buildPropsWithApplicationData([
            {
              name: 'replicaSet my-replica-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'my-cluster',
            },
          ]),
        );

        const cluster = wrapper.find({ label: 'Cluster' }).find(ScopeClusterSelector).first();
        expect(cluster.props().clusters).toEqual([]);
      });
    });
  });

  describe('change handlers', () => {
    it('calls the search service after updating the `Kind` field', () => {
      const wrapper = component({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      const kind = wrapper.find({ label: 'Kind' }).find(Creatable).first();
      kind.props().onChange({ value: 'deployment', label: 'deployment' });
      expect(searchService).toHaveBeenCalledWith('deployment', 'default', 'my-account');
    });

    it('calls the search service after updating the `Namespace` field', () => {
      const wrapper = component({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      const namespace = wrapper.find({ label: 'Namespace' }).find(Creatable).first();
      namespace.props().onChange({ value: 'kube-system', label: 'kube-system' });
      expect(searchService).toHaveBeenCalledWith('configMap', 'kube-system', 'my-account');
    });

    it('calls the search service after updating the `Account` field', () => {
      const wrapper = component({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });
      wrapper.setState({
        accounts: [
          { name: 'my-account', namespaces: ['default'] },
          { name: 'my-other-account', namespaces: ['default'] },
        ],
      } as IManifestSelectorState);

      const account = wrapper.find(AccountSelectInput).first();
      account.props().onChange(createFakeReactSyntheticEvent({ value: 'my-other-account' }));
      expect(searchService).toHaveBeenCalledWith('configMap', 'default', 'my-other-account');
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
      } as IManifestSelectorState);

      const account = wrapper.find(AccountSelectInput).first();
      account.props().onChange(createFakeReactSyntheticEvent({ value: 'my-other-account' }));
      expect(wrapper.instance().state.selector.location).toBeFalsy();
    });
  });

  describe('mode change', () => {
    it('handles kind during static -> dynamic mode transition', () => {
      const wrapper = component(
        {
          manifestName: 'configMap my-config-map',
          account: 'my-account',
          location: 'default',
        },
        { modes: [SelectorMode.Dynamic, SelectorMode.Static] },
      );

      wrapper.find({ id: 'dynamic' }).first().props().onChange();
      expect(wrapper.state().selector.kind).toEqual('configMap');
    });

    it('handles kind during static -> label mode transition', () => {
      const wrapper = component(
        {
          manifestName: 'configMap my-config-map',
          account: 'my-account',
          location: 'default',
        },
        { modes: [SelectorMode.Dynamic, SelectorMode.Static, SelectorMode.Label] },
      );

      wrapper.find({ id: 'label' }).first().props().onChange();
      expect(wrapper.state().selector.kind).toBeNull();
      expect(wrapper.state().selector.kinds).toEqual([]);
      expect(wrapper.state().selector.manifestName).toBeNull();
    });

    it('handles kind during dynamic -> static mode transition', () => {
      const wrapper = component(
        {
          account: 'my-account',
          location: 'default',
          kind: 'configMap',
          mode: SelectorMode.Dynamic,
        },
        { modes: [SelectorMode.Dynamic, SelectorMode.Static] },
      );

      wrapper.find({ id: 'static' }).first().props().onChange();
      // `manifestName` is composed of `${kind} ${resourceName}`
      expect(wrapper.state().selector.manifestName).toEqual('configMap');
    });

    it('handles kind during dynamic -> label mode transition', () => {
      const wrapper = component(
        {
          account: 'my-account',
          location: 'default',
          kind: 'configMap',
          mode: SelectorMode.Dynamic,
        },
        { modes: [SelectorMode.Dynamic, SelectorMode.Static, SelectorMode.Label] },
      );

      wrapper.find({ id: 'label' }).first().props().onChange();
      expect(wrapper.state().selector.kind).toBeNull();
      expect(wrapper.state().selector.kinds).toEqual([]);
      expect(wrapper.state().selector.manifestName).toBeNull();
    });

    it('handles kind during label -> static mode transition', () => {
      const wrapper = component(
        {
          account: 'my-account',
          location: 'default',
          kinds: ['configMap'],
          labelSelectors: {
            selectors: [],
          },
          mode: SelectorMode.Label,
        },
        { modes: [SelectorMode.Dynamic, SelectorMode.Static, SelectorMode.Label] },
      );

      wrapper.find({ id: 'static' }).first().props().onChange();
      expect(wrapper.state().selector.kind).toBeNull();
      expect(wrapper.state().selector.kinds).toBeNull();
    });

    it('handles kind during label -> dynamic mode transition', () => {
      const wrapper = component(
        {
          account: 'my-account',
          location: 'default',
          kinds: ['configMap'],
          labelSelectors: {
            selectors: [],
          },
          mode: SelectorMode.Label,
        },
        { modes: [SelectorMode.Dynamic, SelectorMode.Static, SelectorMode.Label] },
      );

      wrapper.find({ id: 'dynamic' }).first().props().onChange();
      expect(wrapper.state().selector.kind).toBeNull();
      expect(wrapper.state().selector.kinds).toBeNull();
    });
  });
});

const component = (selector: any, props: any = {}) =>
  mount<ManifestSelector>((<ManifestSelector onChange={noop} selector={selector} {...props} />) as any);
