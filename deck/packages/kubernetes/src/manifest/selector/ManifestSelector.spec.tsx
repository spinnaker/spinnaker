import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';

import { AccountService } from '@spinnaker/core';

import type { IManifestSelector } from './IManifestSelector';
import { SelectorMode } from './IManifestSelector';
import { ManifestKindSearchService } from '../ManifestKindSearch';
import { ManifestSelector } from './ManifestSelector';
import { getFormGroupByLabel } from '../../../../core/src/utils/testUtils/rtl';
import { setupUser } from '../../../../core/src/utils/testUtils/userEvent';

describe('<ManifestSelector />', () => {
  const accounts = [
    {
      name: 'my-account',
      namespaces: ['default', 'kube-system', 'other-default'],
      spinnakerKindMap: {
        configMap: 'unclassified',
        deployment: 'serverGroupManagers',
        replicaSet: 'serverGroups',
        statefulSet: 'serverGroups',
      },
    },
    {
      name: 'my-other-account',
      namespaces: ['other-default'],
      spinnakerKindMap: { deployment: 'serverGroupManagers' },
    },
  ] as any;
  let accountService: ReturnType<typeof vi.spyOn>;
  let searchService: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    searchService = vi
      .spyOn(ManifestKindSearchService, 'search')
      .mockResolvedValue([{ name: 'configMap my-config-map' }, { name: 'deployment my-deployment' }] as any);
    accountService = vi.spyOn(AccountService, 'getAllAccountDetailsForProvider').mockResolvedValue(accounts);
  });

  const renderSelector = async (
    selector: Partial<IManifestSelector>,
    props: Partial<React.ComponentProps<typeof ManifestSelector>> = {},
  ) => {
    const onChange = props.onChange || vi.fn();
    const rendered = render(
      <ManifestSelector onChange={onChange} selector={selector as IManifestSelector} {...props} />,
    );
    await waitFor(() => expect(accountService).toHaveBeenCalled());
    await waitFor(() =>
      expect(within(getFormGroupByLabel('Account', rendered.container)).getByRole('combobox')).toBeInTheDocument(),
    );
    return { ...rendered, onChange };
  };

  const getField = (label: string, root: HTMLElement) => getFormGroupByLabel(label, root);
  const getSelectedValue = (label: string, value: string, root: HTMLElement) =>
    within(getField(label, root)).getByText(value, { selector: '.Select-value-label' });

  const chooseReactSelectOption = async (label: string, option: string, root: HTMLElement) => {
    const user = setupUser();
    const field = getField(label, root);
    await user.click(within(field).getByRole('combobox'));
    await user.click(within(field).getByText(option, { selector: '.Select-option' }));
  };

  describe('initialization', () => {
    it('renders namespace from input props', async () => {
      const { container } = await renderSelector({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      expect(getSelectedValue('Namespace', 'default', container)).toBeInTheDocument();
    });

    it('renders kind from input props', async () => {
      const { container } = await renderSelector({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      expect(getSelectedValue('Kind', 'configMap', container)).toBeInTheDocument();
    });

    it('renders name from input props', async () => {
      const { container } = await renderSelector({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
      });

      expect(getSelectedValue('Name', 'my-config-map', container)).toBeInTheDocument();
    });

    it('renders kinds from input props', async () => {
      const { container } = await renderSelector({
        account: 'my-account',
        kinds: ['configMap', 'deployment'],
        location: 'default',
        mode: SelectorMode.Label,
      });

      const kinds = getField('Kinds', container);
      expect(within(kinds).getByText('configMap', { selector: '.Select-value-label' })).toBeInTheDocument();
      expect(within(kinds).getByText('deployment', { selector: '.Select-value-label' })).toBeInTheDocument();
    });

    it('renders labels from input props', async () => {
      const { container } = await renderSelector({
        account: 'my-account',
        kinds: ['configMap', 'deployment'],
        labelSelectors: {
          selectors: [{ key: 'label-key', kind: 'EQUALS', values: ['label-value'] }],
        },
        location: 'default',
        mode: SelectorMode.Label,
      });

      const labels = getField('Labels', container);
      expect(within(labels).getByDisplayValue('label-key')).toBeInTheDocument();
      expect(within(labels).getByDisplayValue('label-value')).toBeInTheDocument();
    });

    describe('cluster dropdown', () => {
      const buildPropsWithApplicationData = (data: any[]) => ({
        modes: [SelectorMode.Static, SelectorMode.Dynamic],
        application: { getDataSource: () => ({ data }) } as any,
      });

      const clusterOptions = async (selector: Partial<IManifestSelector>, data: any[]) => {
        const { container } = await renderSelector(selector, buildPropsWithApplicationData(data));
        return within(getField('Cluster', container));
      };

      it("includes cluster if selected kind matches the cluster's server groups' kind", async () => {
        const cluster = await clusterOptions(
          { kind: 'replicaSet', account: 'my-account', location: 'default', mode: SelectorMode.Dynamic },
          [
            {
              name: 'replicaSet my-replica-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'replicaSet my-replica-set',
            },
          ],
        );

        expect(cluster.getByRole('option', { name: 'replicaSet my-replica-set' })).toBeInTheDocument();
      });

      it("does not include cluster if selected kind does not match cluster's server groups' kind", async () => {
        const cluster = await clusterOptions(
          { kind: 'statefulSet', account: 'my-account', location: 'default', mode: SelectorMode.Dynamic },
          [
            {
              name: 'replicaSet my-replica-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'replicaSet my-replica-set',
            },
          ],
        );

        expect(cluster.queryByRole('option', { name: 'replicaSet my-replica-set' })).not.toBeInTheDocument();
      });

      it('handles case in which a cluster has two different kinds of server groups', async () => {
        const cluster = await clusterOptions(
          { kind: 'statefulSet', account: 'my-account', location: 'default', mode: SelectorMode.Dynamic },
          [
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
          ],
        );

        expect(cluster.getByRole('option', { name: 'my-cluster' })).toBeInTheDocument();
      });

      it("does not include cluster if the cluster's server groups are managed", async () => {
        const cluster = await clusterOptions(
          { kind: 'replicaSet', account: 'my-account', location: 'default', mode: SelectorMode.Dynamic },
          [
            {
              name: 'replicaSet my-replica-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'my-cluster',
              serverGroupManagers: ['deployment my-deployment'],
            },
          ],
        );

        expect(cluster.queryByRole('option', { name: 'my-cluster' })).not.toBeInTheDocument();
      });

      it('filters clusters by account', async () => {
        const cluster = await clusterOptions(
          { kind: 'replicaSet', account: 'my-other-account', location: 'other-default', mode: SelectorMode.Dynamic },
          [
            {
              name: 'replicaSet my-replica-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'my-cluster',
            },
          ],
        );

        expect(cluster.queryByRole('option', { name: 'my-cluster' })).not.toBeInTheDocument();
      });

      it('filters clusters by namespace', async () => {
        const cluster = await clusterOptions(
          { kind: 'replicaSet', account: 'my-account', location: 'kube-system', mode: SelectorMode.Dynamic },
          [
            {
              name: 'replicaSet my-replica-set-v000',
              account: 'my-account',
              region: 'default',
              cluster: 'my-cluster',
            },
          ],
        );

        expect(cluster.queryByRole('option', { name: 'my-cluster' })).not.toBeInTheDocument();
      });
    });
  });

  describe('change handlers', () => {
    it('calls the search service after updating the Kind field', async () => {
      const { container } = await renderSelector({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
        mode: SelectorMode.Static,
      });
      searchService.mockClear();

      await chooseReactSelectOption('Kind', 'deployment', container);

      expect(searchService).toHaveBeenCalledWith('deployment', 'default', 'my-account');
    });

    it('calls the search service after updating the Namespace field', async () => {
      const { container } = await renderSelector({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
        mode: SelectorMode.Static,
      });
      searchService.mockClear();

      await chooseReactSelectOption('Namespace', 'kube-system', container);

      expect(searchService).toHaveBeenCalledWith('configMap', 'kube-system', 'my-account');
    });

    it('uses the Kubernetes provider and searches after updating the Account field', async () => {
      const { container } = await renderSelector({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'other-default',
        mode: SelectorMode.Static,
      });
      await waitFor(() => expect(accountService).toHaveBeenCalledTimes(2));
      expect(accountService.mock.calls.every(([provider]) => provider === 'kubernetes')).toBe(true);
      searchService.mockClear();

      fireEvent.change(within(getField('Account', container)).getByRole('combobox'), {
        target: { value: 'my-other-account' },
      });

      expect(searchService).toHaveBeenCalledWith('configMap', 'other-default', 'my-other-account');
    });

    it('waits for complete manifest search criteria', async () => {
      const { container } = await renderSelector({ account: 'my-account', mode: SelectorMode.Static });
      searchService.mockClear();

      fireEvent.change(within(getField('Account', container)).getByRole('combobox'), {
        target: { value: 'my-other-account' },
      });
      expect(searchService).not.toHaveBeenCalled();

      await chooseReactSelectOption('Namespace', 'other-default', container);
      expect(searchService).not.toHaveBeenCalled();

      await chooseReactSelectOption('Kind', 'deployment', container);
      expect(searchService).toHaveBeenCalledExactlyOnceWith('deployment', 'other-default', 'my-other-account');
    });

    it('clears namespace when changing account if account does not have selected namespace', async () => {
      const { container } = await renderSelector({
        manifestName: 'configMap my-config-map',
        account: 'my-account',
        location: 'default',
        mode: SelectorMode.Static,
      });
      expect(getSelectedValue('Namespace', 'default', container)).toBeInTheDocument();

      fireEvent.change(within(getField('Account', container)).getByRole('combobox'), {
        target: { value: 'my-other-account' },
      });

      expect(
        within(getField('Namespace', container)).queryByText('default', { selector: '.Select-value-label' }),
      ).not.toBeInTheDocument();
    });
  });

  describe('mode change', () => {
    const modes = [SelectorMode.Dynamic, SelectorMode.Static, SelectorMode.Label];

    it('renders the static kind in dynamic mode after a static to dynamic transition', async () => {
      const user = setupUser();
      const { container, onChange } = await renderSelector(
        {
          manifestName: 'configMap my-config-map',
          account: 'my-account',
          location: 'default',
          mode: SelectorMode.Static,
        },
        { modes },
      );

      await user.click(screen.getByRole('radio', { name: 'Choose a target dynamically' }));

      expect(onChange).toHaveBeenLastCalledWith(
        expect.objectContaining({ kind: 'configMap', mode: SelectorMode.Dynamic }),
      );
      expect(getSelectedValue('Kind', 'configMap', container)).toBeInTheDocument();
      expect(screen.getByText('Cluster', { selector: '.label-text' })).toBeInTheDocument();
      expect(screen.queryByText('Name', { selector: '.label-text' })).not.toBeInTheDocument();
    });

    it('renders label fields after a static to label transition', async () => {
      const user = setupUser();
      const { container, onChange } = await renderSelector(
        {
          manifestName: 'configMap my-config-map',
          account: 'my-account',
          location: 'default',
          mode: SelectorMode.Static,
        },
        { modes },
      );
      (onChange as ReturnType<typeof vi.fn>).mockClear();

      await user.click(screen.getByRole('radio', { name: 'Match target(s) by label' }));

      expect(within(container).getByText('Kinds', { selector: '.label-text' })).toBeInTheDocument();
      expect(within(container).getByText('Labels', { selector: '.label-text' })).toBeInTheDocument();
      expect(within(container).queryByText('Name', { selector: '.label-text' })).not.toBeInTheDocument();
      expect(onChange).toHaveBeenLastCalledWith({
        account: 'my-account',
        cluster: null,
        criteria: null,
        kind: null,
        kinds: [],
        labelSelectors: { selectors: [] },
        location: 'default',
        manifestName: null,
        mode: SelectorMode.Label,
      });
    });

    it('renders a static name after a dynamic to static transition', async () => {
      const user = setupUser();
      const { container, onChange } = await renderSelector(
        { account: 'my-account', location: 'default', kind: 'configMap', mode: SelectorMode.Dynamic },
        { modes },
      );
      (onChange as ReturnType<typeof vi.fn>).mockClear();

      await user.click(screen.getByRole('radio', { name: 'Choose a static target' }));

      expect(within(container).getByText('Name', { selector: '.label-text' })).toBeInTheDocument();
      expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ manifestName: 'configMap' }));
    });

    it('renders label fields after a dynamic to label transition', async () => {
      const user = setupUser();
      const { container, onChange } = await renderSelector(
        { account: 'my-account', location: 'default', kind: 'configMap', mode: SelectorMode.Dynamic },
        { modes },
      );
      (onChange as ReturnType<typeof vi.fn>).mockClear();

      await user.click(screen.getByRole('radio', { name: 'Match target(s) by label' }));

      expect(within(container).getByText('Kinds', { selector: '.label-text' })).toBeInTheDocument();
      expect(within(container).getByText('Labels', { selector: '.label-text' })).toBeInTheDocument();
      expect(onChange).toHaveBeenLastCalledWith({
        account: 'my-account',
        cluster: null,
        criteria: null,
        kind: null,
        kinds: [],
        labelSelectors: { selectors: [] },
        location: 'default',
        manifestName: null,
        mode: SelectorMode.Label,
      });
    });

    it('renders static fields after a label to static transition', async () => {
      const user = setupUser();
      const { container, onChange } = await renderSelector(
        {
          account: 'my-account',
          location: 'default',
          kinds: ['configMap'],
          labelSelectors: { selectors: [] },
          mode: SelectorMode.Label,
        },
        { modes },
      );
      (onChange as ReturnType<typeof vi.fn>).mockClear();

      await user.click(screen.getByRole('radio', { name: 'Choose a static target' }));

      expect(within(container).getByText('Name', { selector: '.label-text' })).toBeInTheDocument();
      expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ kind: null, kinds: null }));
    });

    it('renders dynamic fields after a label to dynamic transition', async () => {
      const user = setupUser();
      const { container, onChange } = await renderSelector(
        {
          account: 'my-account',
          location: 'default',
          kinds: ['configMap'],
          labelSelectors: { selectors: [] },
          mode: SelectorMode.Label,
        },
        { modes },
      );
      (onChange as ReturnType<typeof vi.fn>).mockClear();

      await user.click(screen.getByRole('radio', { name: 'Choose a target dynamically' }));

      expect(within(container).getByText('Cluster', { selector: '.label-text' })).toBeInTheDocument();
      expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ kind: null, kinds: null }));
    });

    it('does not mutate prior callback payloads during later mode transitions', async () => {
      const user = setupUser();
      const onChange = vi.fn();
      await renderSelector(
        {
          manifestName: 'configMap my-config-map',
          account: 'my-account',
          location: 'default',
          mode: SelectorMode.Static,
        },
        { modes, onChange },
      );
      onChange.mockClear();

      await user.click(screen.getByRole('radio', { name: 'Choose a target dynamically' }));
      const dynamicPayload = onChange.mock.calls.at(-1)![0];
      expect(dynamicPayload.mode).toBe(SelectorMode.Dynamic);

      await user.click(screen.getByRole('radio', { name: 'Match target(s) by label' }));

      expect(dynamicPayload.mode).toBe(SelectorMode.Dynamic);
      expect(dynamicPayload.kinds).toBeNull();
      expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ mode: SelectorMode.Label, kinds: [] }));
    });
  });
});
