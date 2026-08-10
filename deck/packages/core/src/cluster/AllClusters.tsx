import React from 'react';

import { AllClustersGroupings } from './AllClustersGroupings';
import type { IAccountDetails } from '../account';
import type { Application } from '../application';
import { BannerContainer } from '../banner';
import { useDeckRuntimeServices } from '../bootstrap/DeckRuntimeContext';
import type { ICloudProviderConfig } from '../cloudProvider';
import { CloudProviderRegistry, ProviderSelectionService } from '../cloudProvider';
import type { IFilterTag, ISortFilter } from '../filterModel';
import { FilterTags } from '../filterModel';
import { OnDemandClusterPicker } from './onDemand/OnDemandClusterPicker';
import { ClusterState } from '../state';
import { Spinner } from '../widgets';

export interface IAllClustersProps {
  app: Application;
  initialized: boolean;
  loadError?: boolean;
}

export interface IAllClustersState {
  sortFilter: ISortFilter;
  tags: IFilterTag[];
}

export interface IClusterControlsProps {
  showInstancesToggle: boolean;
  sortFilter: ISortFilter;
  updateClusterGroups: () => void;
}

export function hasReactCloneServerGroupModal(
  _application: Application,
  _account: IAccountDetails,
  provider: ICloudProviderConfig,
): boolean {
  return Boolean(provider?.serverGroup?.CloneServerGroupModal);
}

export function useAllClustersState(): IAllClustersState {
  const [sortFilter, setSortFilter] = React.useState<ISortFilter>(ClusterState.filterModel.asFilterModel.sortFilter);
  const [tags, setTags] = React.useState<IFilterTag[]>(ClusterState.filterModel.asFilterModel.tags || []);

  React.useEffect(() => {
    const subscription = ClusterState.filterService.groupsUpdatedStream.subscribe(() => {
      setSortFilter({ ...ClusterState.filterModel.asFilterModel.sortFilter });
      setTags([...(ClusterState.filterModel.asFilterModel.tags || [])]);
    });

    return () => subscription.unsubscribe();
  }, []);

  return { sortFilter, tags };
}

export function updateAllClusterGroups(app: Application): void {
  ClusterState.filterModel.asFilterModel.applyParamsToUrl();
  ClusterState.filterService.updateClusterGroups(app);
}

export function ClusterControls({ showInstancesToggle, sortFilter, updateClusterGroups }: IClusterControlsProps) {
  const handleMultiselectToggle = (): void => {
    ClusterState.filterModel.asFilterModel.sortFilter = {
      ...ClusterState.filterModel.asFilterModel.sortFilter,
      multiselect: !sortFilter.multiselect,
    };
    ClusterState.multiselectModel.syncNavigation();
    updateClusterGroups();
  };

  const handleShowAllInstancesChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    ClusterState.filterModel.asFilterModel.sortFilter = {
      ...ClusterState.filterModel.asFilterModel.sortFilter,
      showAllInstances: event.target.checked,
      listInstances: event.target.checked ? sortFilter.listInstances : false,
    };
    updateClusterGroups();
  };

  const handleListInstancesChange = (event: React.ChangeEvent<HTMLInputElement>): void => {
    ClusterState.filterModel.asFilterModel.sortFilter = {
      ...ClusterState.filterModel.asFilterModel.sortFilter,
      listInstances: event.target.checked,
    };
    updateClusterGroups();
  };

  return (
    <div className="form-inline clearfix filters">
      <div className="form-group">
        <button
          className={`btn btn-xs btn-default${sortFilter.multiselect ? ' active' : ''}`}
          onClick={handleMultiselectToggle}
        >
          <span className="glyphicon glyphicon-list visible-lg-inline" />
          <span className="glyphicon glyphicon-list visible-md-inline visible-sm-inline" />
          <span className="visible-lg-inline">Edit multiple</span>
        </button>
      </div>
      <div className="form-group">
        <label className="checkbox">Show </label>
        {showInstancesToggle && (
          <div className="checkbox">
            <label>
              <input
                type="checkbox"
                checked={Boolean(sortFilter.showAllInstances)}
                onChange={handleShowAllInstancesChange}
              />
              Instances
            </label>
          </div>
        )}
        {sortFilter.showAllInstances && (
          <div className="checkbox">
            <label>
              <input type="checkbox" checked={Boolean(sortFilter.listInstances)} onChange={handleListInstancesChange} />
              with details
            </label>
          </div>
        )}
      </div>
    </div>
  );
}

export function CreateServerGroupButton({ app }: { app: Application }) {
  const runtimeServices = useDeckRuntimeServices();
  const { serverGroupCommandBuilder } = runtimeServices;
  const [disabled, setDisabled] = React.useState(true);
  const [createServerGroupError, setCreateServerGroupError] = React.useState<string | null>(null);

  React.useEffect(() => {
    let active = true;
    ProviderSelectionService.isDisabled(app).then((isDisabled) => active && setDisabled(isDisabled));

    return () => {
      active = false;
    };
  }, [app]);

  const createServerGroup = (): void => {
    setCreateServerGroupError(null);
    ProviderSelectionService.selectProvider(app, 'serverGroup', hasReactCloneServerGroupModal)
      .then((provider) => {
        return serverGroupCommandBuilder.buildNewServerGroupCommand(app, provider, null).then((command: any) => {
          const providerConfig = CloudProviderRegistry.getValue(provider, 'serverGroup');
          if (!providerConfig.CloneServerGroupModal) {
            throw new Error(`No React clone server group modal is registered for provider "${provider}".`);
          }

          providerConfig.CloneServerGroupModal.show(
            {
              title: 'Create New Server Group',
              application: app,
              serverGroup: null,
              command,
              provider,
              isNew: true,
            },
            runtimeServices,
          );
        });
      })
      .catch((error) => {
        if (error instanceof Error) {
          setCreateServerGroupError(error.message);
        }
      });
  };

  if (disabled) {
    return null;
  }

  return (
    <>
      <button className="btn btn-sm btn-default" onClick={createServerGroup}>
        <span className="glyphicon glyphicon-plus-sign visible-lg-inline" />
        <span className="glyphicon glyphicon-plus-sign visible-md-inline visible-sm-inline" />
        <span className="visible-lg-inline">Create Server Group</span>
      </button>
      {createServerGroupError && <div className="alert alert-warning">{createServerGroupError}</div>}
    </>
  );
}

export function AllClusters({ app, initialized, loadError }: IAllClustersProps) {
  const { sortFilter, tags } = useAllClustersState();
  const dataSource = app.getDataSource('serverGroups');
  const updateClusterGroups = (): void => updateAllClusterGroups(app);
  const clearFilters = (): void => {
    ClusterState.filterService.clearFilters();
    updateClusterGroups();
  };

  return (
    <div className="main-content">
      {initialized && (
        <div className="header row header-clusters">
          <div className="col-lg-9 col-md-10 col-sm-10">
            <ClusterControls
              showInstancesToggle={true}
              sortFilter={sortFilter}
              updateClusterGroups={updateClusterGroups}
            />
          </div>
          <div className="col-lg-3 col-md-2 col-sm-2">
            <div className="application-actions">
              <CreateServerGroupButton app={app} />
            </div>
          </div>
          {!dataSource.fetchOnDemand && (
            <FilterTags tags={tags} tagCleared={updateClusterGroups} clearFilters={clearFilters} />
          )}
        </div>
      )}

      {dataSource.fetchOnDemand && <OnDemandClusterPicker application={app} />}

      {loadError && (
        <div>
          <h4 className="text-center">
            There was an error loading the clusters for this application. We'll try again shortly.
          </h4>
        </div>
      )}

      {!initialized && (
        <div>
          <Spinner size="medium" />
        </div>
      )}

      {initialized && <BannerContainer app={app} />}

      <div className="content" style={{ overflowY: 'hidden' }}>
        <AllClustersGroupings app={app} initialized={initialized} />
      </div>
    </div>
  );
}
