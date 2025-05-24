package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.kork.web.selector.SelectableService;
import com.netflix.spinnaker.orca.clouddriver.model.OperationContext;
import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskId;
import java.util.Collection;
import java.util.Map;
import okhttp3.ResponseBody;
import retrofit2.Call;

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
  public Call<TaskId> requestOperations(
      String clientRequestId, Collection<Map<String, Map>> operations) {
    return getService().requestOperations(clientRequestId, operations);
  }

  @Override
  public Call<TaskId> requestOperations(
      String clientRequestId, String cloudProvider, Collection<Map<String, Map>> operations) {
    return getService().requestOperations(clientRequestId, cloudProvider, operations);
  }

  @Override
  public Call<ResponseBody> submitOperation(
      String clientRequestId,
      String cloudProvider,
      String operationName,
      OperationContext operation) {
    return getService().submitOperation(clientRequestId, cloudProvider, operationName, operation);
  }

  @Override
  public Call<TaskId> updateTask(String cloudProvider, String id, Map details) {
    return getService().updateTask(cloudProvider, id, details);
  }

  @Override
  public Call<TaskId> restartTaskViaOperations(
      String cloudProvider, String id, Collection<Map<String, Map>> operations) {
    return getService().restartTaskViaOperations(cloudProvider, id, operations);
  }

  @Override
  public Call<ResponseBody> collectJob(String app, String account, String region, String id) {
    return getService().collectJob(app, account, region, id);
  }

  @Override
  public Call<ResponseBody> cancelJob(String app, String account, String region, String id) {
    return getService().cancelJob(app, account, region, id);
  }

  @Override
  public Call<Map<String, Object>> getFileContents(
      String app, String account, String region, String id, String fileName) {
    return getService().getFileContents(app, account, region, id, fileName);
  }

  @Override
  public Call<Map<String, Object>> getFileContentsFromKubernetesPod(
      String app, String account, String namespace, String podName, String fileName) {
    return getService()
        .getFileContentsFromKubernetesPod(app, account, namespace, podName, fileName);
  }

  @Override
  public Call<Task> lookupTask(String id) {
    return getService().lookupTask(id);
  }

  @Override
  public Call<TaskId> resumeTask(String id) {
    return getService().resumeTask(id);
  }
}
