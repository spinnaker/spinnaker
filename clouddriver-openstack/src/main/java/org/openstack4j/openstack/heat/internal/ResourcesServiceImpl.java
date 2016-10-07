package org.openstack4j.openstack.heat.internal;

import com.google.common.net.*;
import org.openstack4j.api.heat.ResourcesService;
import org.openstack4j.model.common.*;
import org.openstack4j.model.heat.Resource;
import org.openstack4j.model.heat.ResourceHealth;
import org.openstack4j.openstack.heat.domain.HeatResource;
import org.openstack4j.openstack.heat.domain.HeatResource.Resources;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * TODO remove once openstack4j 3.0.3 is released
 * This class implements some methods for manipulation of {@link HeatResource} objects. The
 * non-exhaustive list of methods is oriented along
 * http://developer.openstack.org/api-ref-orchestration-v1.html
 *
 * @author Octopus Zhang
 *
 */
public class ResourcesServiceImpl extends BaseHeatServices implements ResourcesService {

  @Override
  public List<? extends Resource> list(String stackName, String stackId) {
    checkNotNull(stackName);
    checkNotNull(stackId);
    return get(Resources.class, uri("/stacks/%s/%s/resources", stackName, stackId)).execute().getList();
  }

  @Override
  public List<? extends Resource> list(String stackNameOrId) {
    return list(stackNameOrId, 1);
  }

  @Override
  public List<? extends Resource> list(String stackNameOrId, int depth) {
    checkNotNull(stackNameOrId);
    return get(HeatResource.Resources.class, uri("/stacks/%s/resources", stackNameOrId))
      .param("nested_depth", depth).execute().getList();
  }

  @Override
  public Resource show(String stackName, String stackId, String resourceName) {
    checkNotNull(stackName);
    checkNotNull(stackId);
    checkNotNull(resourceName);
    return get(HeatResource.class, uri("/stacks/%s/%s/resources/%s", stackName, stackId, resourceName)).execute();
  }

  @SuppressWarnings("unchecked")
  @Override
  public Map<String, Object> getMetadata(String stackName, String stackId, String resourceName) {
    checkNotNull(stackName);
    checkNotNull(stackId);
    checkNotNull(resourceName);
    return get(HashMap.class, uri("/stacks/%s/%s/resources/%s/metadata", stackName, stackId, resourceName)).execute();
  }

  @Override
  public ActionResponse signal(String stackName, String stackId, String resourceName, String jsonData) {
    checkNotNull(stackName);
    checkNotNull(stackId);
    checkNotNull(resourceName);
    return postWithResponse(uri("/stacks/%s/%s/resources/%s/signal", stackName, stackId, resourceName))
      .json(jsonData)
      .execute();
  }

  @Override
  public ActionResponse markUnhealthy(String stackName, String stackId, String resourceName, ResourceHealth resourceHealth) {
    checkNotNull(stackName);
    checkNotNull(stackId);
    return patchWithResponse(uri("/stacks/%s/%s/resources/%s", stackName, stackId, resourceName))
      .entity(resourceHealth)
      .execute();
  }
}
