import React, { useEffect } from 'react';

import type { ILoadBalancer, IStageConfigProps } from '@spinnaker/core';

import { AppengineCreateLoadBalancerModal } from '../../../loadBalancer/configure/AppengineCreateLoadBalancerModal';

export function AppengineEditLoadBalancerStageConfig({ application, stage, updateStage }: IStageConfigProps) {
  useEffect(() => {
    stage.loadBalancers = stage.loadBalancers || [];
    stage.cloudProvider = 'appengine';
    updateStage(stage);
  }, []);

  const setLoadBalancers = (loadBalancers: ILoadBalancer[]) => {
    stage.loadBalancers = loadBalancers;
    updateStage(stage);
  };

  const addLoadBalancer = () => {
    AppengineCreateLoadBalancerModal.show({
      app: application,
      loadBalancer: null,
      forPipelineConfig: true,
      isNew: true,
    } as any)
      .then((loadBalancer) => setLoadBalancers([...(stage.loadBalancers || []), loadBalancer]))
      .catch(() => {});
  };

  const editLoadBalancer = (index: number) => {
    AppengineCreateLoadBalancerModal.show({
      app: application,
      loadBalancer: stage.loadBalancers[index],
      forPipelineConfig: true,
      isNew: false,
    } as any)
      .then((loadBalancer) => {
        const loadBalancers = [...stage.loadBalancers];
        loadBalancers[index] = loadBalancer;
        setLoadBalancers(loadBalancers);
      })
      .catch(() => {});
  };

  const removeLoadBalancer = (index: number) => {
    setLoadBalancers(stage.loadBalancers.filter((_loadBalancer: ILoadBalancer, i: number) => i !== index));
  };

  return (
    <div className="well well-sm clearfix">
      <h4>Load Balancers</h4>
      <table className="table table-condensed">
        <thead>
          <tr>
            <th>Account</th>
            <th>Name</th>
            <th>Region</th>
            <th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {(stage.loadBalancers || []).map((loadBalancer: any, index: number) => (
            <tr key={`${loadBalancer.name}-${index}`}>
              <td>{loadBalancer.credentials || loadBalancer.account}</td>
              <td>{loadBalancer.name}</td>
              <td>{loadBalancer.region}</td>
              <td className="condensed-actions">
                <button className="btn btn-sm btn-link" onClick={() => editLoadBalancer(index)} type="button">
                  <span className="glyphicon glyphicon-edit" />
                </button>
                <button
                  className="btn btn-sm btn-link pad-left"
                  onClick={() => removeLoadBalancer(index)}
                  type="button"
                >
                  <span className="glyphicon glyphicon-trash" />
                </button>
              </td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={4}>
              <button className="btn btn-block btn-sm add-new" onClick={addLoadBalancer} type="button">
                <span className="glyphicon glyphicon-plus-sign" /> Add load balancer
              </button>
            </td>
          </tr>
        </tfoot>
      </table>
    </div>
  );
}
