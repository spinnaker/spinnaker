package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.kork.web.selector.SelectableService;
import com.netflix.spinnaker.orca.clouddriver.model.OperationContext;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import java.util.Collection;
import java.util.Map;
import retrofit.client.Response;

/**
 * Wrapper around {@link KatoRestService} which selects an endpoint based on {@link
 * SelectableService.Criteria}. This can be configured to send requests to a specific Clouddriver
 * endpoint based upon predfined crtieria, for example cloud provider or account. Defaults to the
 * default Clouddriver URL if no crtieria are configured.
 */
public class DelegatingKatoRestService extends DelegatingClouddriverService<KatoRestService>
    implements KatoRestService {

  public DelegatingKatoRestService(SelectableService selectableService) {
    super(selectableService);
  }

  @Override
  public TaskId requestOperations(
      String clientRequestId, Collection<? extends Map<String, Map>> operations) {
    return getService().requestOperations(clientRequestId, operations);
  }

  @Override
  public TaskId requestOperations(
      String clientRequestId,
      String cloudProvider,
      Collection<? extends Map<String, Map>> operations) {
    return getService().requestOperations(clientRequestId, cloudProvider, operations);
  }

  @Override
  public Response submitOperation(
      String clientRequestId,
      String cloudProvider,
      String operationName,
      OperationContext operation) {
    return getService().submitOperation(clientRequestId, cloudProvider, operationName, operation);
  }

  @Override
  public TaskId updateTask(String cloudProvider, String id, Map details) {
    return getService().updateTask(cloudProvider, id, details);
  }

  @Override
  public TaskId restartTaskViaOperations(
      String cloudProvider, String id, Collection<? extends Map<String, Map>> operations) {
    return getService().restartTaskViaOperations(cloudProvider, id, operations);
  }

  @Override
  public Response collectJob(String app, String account, String region, String id) {
    return getService().collectJob(app, account, region, id);
  }

  @Override
  public Response cancelJob(String app, String account, String region, String id) {
    return getService().cancelJob(app, account, region, id);
  }

  @Override
  public Map<String, Object> getFileContents(
      String app, String account, String region, String id, String fileName) {
    return getService().getFileContents(app, account, region, id, fileName);
  }

  @Override
  public Task lookupTask(String id) {
    return getService().lookupTask(id);
  }

  @Override
  public TaskId resumeTask(String id) {
    return getService().resumeTask(id);
  }
}
