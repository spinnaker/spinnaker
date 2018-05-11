# \ExecutionsControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetLatestExecutionsByConfigIdsUsingGET**](ExecutionsControllerApi.md#GetLatestExecutionsByConfigIdsUsingGET) | **Get** /executions | Retrieve a list of the most recent pipeline executions for the provided &#x60;pipelineConfigIds&#x60; that match the provided &#x60;statuses&#x60; query parameter


# **GetLatestExecutionsByConfigIdsUsingGET**
> []interface{} GetLatestExecutionsByConfigIdsUsingGET(ctx, pipelineConfigIds, optional)
Retrieve a list of the most recent pipeline executions for the provided `pipelineConfigIds` that match the provided `statuses` query parameter

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **pipelineConfigIds** | **string**| pipelineConfigIds | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **pipelineConfigIds** | **string**| pipelineConfigIds | 
 **limit** | **int32**| limit | 
 **statuses** | **string**| statuses | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

