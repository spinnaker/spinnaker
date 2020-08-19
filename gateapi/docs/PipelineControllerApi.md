# \PipelineControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**CancelPipelineUsingPUT1**](PipelineControllerApi.md#CancelPipelineUsingPUT1) | **Put** /pipelines/{id}/cancel | Cancel a pipeline execution
[**DeletePipelineUsingDELETE**](PipelineControllerApi.md#DeletePipelineUsingDELETE) | **Delete** /pipelines/{application}/{pipelineName} | Delete a pipeline definition
[**DeletePipelineUsingDELETE1**](PipelineControllerApi.md#DeletePipelineUsingDELETE1) | **Delete** /pipelines/{id} | Delete a pipeline execution
[**EvaluateExpressionForExecutionAtStageUsingGET**](PipelineControllerApi.md#EvaluateExpressionForExecutionAtStageUsingGET) | **Get** /pipelines/{id}/{stageId}/evaluateExpression | Evaluate a pipeline expression at a specific stage using the provided execution as context
[**EvaluateExpressionForExecutionUsingGET**](PipelineControllerApi.md#EvaluateExpressionForExecutionUsingGET) | **Get** /pipelines/{id}/evaluateExpression | Evaluate a pipeline expression using the provided execution as context
[**EvaluateExpressionForExecutionViaPOSTUsingPOST**](PipelineControllerApi.md#EvaluateExpressionForExecutionViaPOSTUsingPOST) | **Post** /pipelines/{id}/evaluateExpression | Evaluate a pipeline expression using the provided execution as context
[**EvaluateVariablesUsingPOST**](PipelineControllerApi.md#EvaluateVariablesUsingPOST) | **Post** /pipelines/{id}/evaluateVariables | Evaluate variables same as Evaluate Variables stage using the provided execution as context
[**GetPipelineUsingGET**](PipelineControllerApi.md#GetPipelineUsingGET) | **Get** /pipelines/{id} | Retrieve a pipeline execution
[**InvokePipelineConfigUsingPOST1**](PipelineControllerApi.md#InvokePipelineConfigUsingPOST1) | **Post** /pipelines/{application}/{pipelineNameOrId} | Trigger a pipeline execution
[**InvokePipelineConfigViaEchoUsingPOST**](PipelineControllerApi.md#InvokePipelineConfigViaEchoUsingPOST) | **Post** /pipelines/v2/{application}/{pipelineNameOrId} | Trigger a pipeline execution
[**PausePipelineUsingPUT**](PipelineControllerApi.md#PausePipelineUsingPUT) | **Put** /pipelines/{id}/pause | Pause a pipeline execution
[**RenamePipelineUsingPOST**](PipelineControllerApi.md#RenamePipelineUsingPOST) | **Post** /pipelines/move | Rename a pipeline definition
[**RestartStageUsingPUT**](PipelineControllerApi.md#RestartStageUsingPUT) | **Put** /pipelines/{id}/stages/{stageId}/restart | Restart a stage execution
[**ResumePipelineUsingPUT**](PipelineControllerApi.md#ResumePipelineUsingPUT) | **Put** /pipelines/{id}/resume | Resume a pipeline execution
[**SavePipelineUsingPOST**](PipelineControllerApi.md#SavePipelineUsingPOST) | **Post** /pipelines | Save a pipeline definition
[**StartUsingPOST**](PipelineControllerApi.md#StartUsingPOST) | **Post** /pipelines/start | Initiate a pipeline execution
[**UpdatePipelineUsingPUT**](PipelineControllerApi.md#UpdatePipelineUsingPUT) | **Put** /pipelines/{id} | Update a pipeline definition
[**UpdateStageUsingPATCH**](PipelineControllerApi.md#UpdateStageUsingPATCH) | **Patch** /pipelines/{id}/stages/{stageId} | Update a stage execution


# **CancelPipelineUsingPUT1**
> CancelPipelineUsingPUT1(ctx, id, optional)
Cancel a pipeline execution

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 
 **optional** | ***PipelineControllerApiCancelPipelineUsingPUT1Opts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a PipelineControllerApiCancelPipelineUsingPUT1Opts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **force** | **optional.Bool**| force | [default to false]
 **reason** | **optional.String**| reason | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DeletePipelineUsingDELETE**
> DeletePipelineUsingDELETE(ctx, application, pipelineName)
Delete a pipeline definition

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **pipelineName** | **string**| pipelineName | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DeletePipelineUsingDELETE1**
> map[string]interface{} DeletePipelineUsingDELETE1(ctx, id)
Delete a pipeline execution

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **EvaluateExpressionForExecutionAtStageUsingGET**
> map[string]interface{} EvaluateExpressionForExecutionAtStageUsingGET(ctx, expression, id, stageId)
Evaluate a pipeline expression at a specific stage using the provided execution as context

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **expression** | **string**| expression | 
  **id** | **string**| id | 
  **stageId** | **string**| stageId | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **EvaluateExpressionForExecutionUsingGET**
> map[string]interface{} EvaluateExpressionForExecutionUsingGET(ctx, expression, id)
Evaluate a pipeline expression using the provided execution as context

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **expression** | **string**| expression | 
  **id** | **string**| id | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **EvaluateExpressionForExecutionViaPOSTUsingPOST**
> map[string]interface{} EvaluateExpressionForExecutionViaPOSTUsingPOST(ctx, id, pipelineExpression)
Evaluate a pipeline expression using the provided execution as context

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 
  **pipelineExpression** | **string**| pipelineExpression | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: text/plain
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **EvaluateVariablesUsingPOST**
> map[string]interface{} EvaluateVariablesUsingPOST(ctx, executionId, expressions, optional)
Evaluate variables same as Evaluate Variables stage using the provided execution as context

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **executionId** | **string**| Execution id to run against | 
  **expressions** | [**[]Mapstringstring**](Map«string,string».md)| List of variables/expressions to evaluate | 
 **optional** | ***PipelineControllerApiEvaluateVariablesUsingPOSTOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a PipelineControllerApiEvaluateVariablesUsingPOSTOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **requisiteStageRefIds** | **optional.String**| Comma separated list of requisite stage IDs for the evaluation stage | 
 **spelVersion** | **optional.String**| Version of SpEL evaluation logic to use (v3 or v4) | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetPipelineUsingGET**
> interface{} GetPipelineUsingGET(ctx, id)
Retrieve a pipeline execution

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **InvokePipelineConfigUsingPOST1**
> InvokePipelineConfigUsingPOST1(ctx, application, pipelineNameOrId, optional)
Trigger a pipeline execution

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **pipelineNameOrId** | **string**| pipelineNameOrId | 
 **optional** | ***PipelineControllerApiInvokePipelineConfigUsingPOST1Opts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a PipelineControllerApiInvokePipelineConfigUsingPOST1Opts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **trigger** | [**optional.Interface of interface{}**](interface{}.md)| trigger | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **InvokePipelineConfigViaEchoUsingPOST**
> interface{} InvokePipelineConfigViaEchoUsingPOST(ctx, application, pipelineNameOrId, optional)
Trigger a pipeline execution

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **application** | **string**| application | 
  **pipelineNameOrId** | **string**| pipelineNameOrId | 
 **optional** | ***PipelineControllerApiInvokePipelineConfigViaEchoUsingPOSTOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a PipelineControllerApiInvokePipelineConfigViaEchoUsingPOSTOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **trigger** | [**optional.Interface of interface{}**](interface{}.md)| trigger | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **PausePipelineUsingPUT**
> PausePipelineUsingPUT(ctx, id)
Pause a pipeline execution

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **RenamePipelineUsingPOST**
> RenamePipelineUsingPOST(ctx, renameCommand)
Rename a pipeline definition

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **renameCommand** | [**interface{}**](interface{}.md)| renameCommand | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **RestartStageUsingPUT**
> map[string]interface{} RestartStageUsingPUT(ctx, context, id, stageId)
Restart a stage execution

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **context** | [**interface{}**](interface{}.md)| context | 
  **id** | **string**| id | 
  **stageId** | **string**| stageId | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ResumePipelineUsingPUT**
> map[string]interface{} ResumePipelineUsingPUT(ctx, id)
Resume a pipeline execution

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **SavePipelineUsingPOST**
> SavePipelineUsingPOST(ctx, pipeline, optional)
Save a pipeline definition

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **pipeline** | [**interface{}**](interface{}.md)| pipeline | 
 **optional** | ***PipelineControllerApiSavePipelineUsingPOSTOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a PipelineControllerApiSavePipelineUsingPOSTOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **staleCheck** | **optional.Bool**| staleCheck | [default to false]

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **StartUsingPOST**
> ResponseEntity StartUsingPOST(ctx, map_)
Initiate a pipeline execution

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **map_** | [**interface{}**](interface{}.md)| map | 

### Return type

[**ResponseEntity**](ResponseEntity.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **UpdatePipelineUsingPUT**
> map[string]interface{} UpdatePipelineUsingPUT(ctx, id, pipeline)
Update a pipeline definition

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 
  **pipeline** | [**interface{}**](interface{}.md)| pipeline | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **UpdateStageUsingPATCH**
> map[string]interface{} UpdateStageUsingPATCH(ctx, context, id, stageId)
Update a stage execution

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **context** | [**interface{}**](interface{}.md)| context | 
  **id** | **string**| id | 
  **stageId** | **string**| stageId | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

