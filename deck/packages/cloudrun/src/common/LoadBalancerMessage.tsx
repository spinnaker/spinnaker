import React from 'react';

export function LoadBalancerMessage({ showCreateMessage = false }: { showCreateMessage?: boolean }) {
  return (
    <div className="row">
      <div className="col-md-offset-1 col-md-10">
        <div className="well">
          <p>
            {showCreateMessage && <span>Spinnaker cannot create a load balancer for Cloud Run. </span>}A Spinnaker load
            balancer maps to an Cloud Run Service, which will be created automatically alongside a Revision. Once
            created, the Service can be edited as a Load Balancer.
          </p>
        </div>
      </div>
    </div>
  );
}
