# \PipelineConfigControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**ConvertPipelineConfigToPipelineTemplateUsingGET**](PipelineConfigControllerApi.md#ConvertPipelineConfigToPipelineTemplateUsingGET) | **Get** /pipelineConfigs/{pipelineConfigId}/convertToTemplate | Convert a pipeline config to a pipeline template.
[**GetAllPipelineConfigsUsingGET**](PipelineConfigControllerApi.md#GetAllPipelineConfigsUsingGET) | **Get** /pipelineConfigs | Get all pipeline configs.
[**GetPipelineConfigHistoryUsingGET**](PipelineConfigControllerApi.md#GetPipelineConfigHistoryUsingGET) | **Get** /pipelineConfigs/{pipelineConfigId}/history | Get pipeline config history.


# **ConvertPipelineConfigToPipelineTemplateUsingGET**
> string ConvertPipelineConfigToPipelineTemplateUsingGET(ctx, pipelineConfigId)
Convert a pipeline config to a pipeline template.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **pipelineConfigId** | **string**| pipelineConfigId | 

### Return type

**string**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetAllPipelineConfigsUsingGET**
> []interface{} GetAllPipelineConfigsUsingGET(ctx, )
Get all pipeline configs.

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetPipelineConfigHistoryUsingGET**
> []interface{} GetPipelineConfigHistoryUsingGET(ctx, pipelineConfigId, optional)
Get pipeline config history.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **pipelineConfigId** | **string**| pipelineConfigId | 
 **optional** | ***PipelineConfigControllerApiGetPipelineConfigHistoryUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a PipelineConfigControllerApiGetPipelineConfigHistoryUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **limit** | **optional.Int32**| limit | [default to 20]

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

