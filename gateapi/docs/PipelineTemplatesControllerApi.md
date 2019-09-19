# \PipelineTemplatesControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**CreateUsingPOST**](PipelineTemplatesControllerApi.md#CreateUsingPOST) | **Post** /pipelineTemplates | Create a pipeline template.
[**DeleteUsingDELETE**](PipelineTemplatesControllerApi.md#DeleteUsingDELETE) | **Delete** /pipelineTemplates/{id} | Delete a pipeline template.
[**GetUsingGET**](PipelineTemplatesControllerApi.md#GetUsingGET) | **Get** /pipelineTemplates/{id} | Get a pipeline template.
[**ListPipelineTemplateDependentsUsingGET**](PipelineTemplatesControllerApi.md#ListPipelineTemplateDependentsUsingGET) | **Get** /pipelineTemplates/{id}/dependents | List all pipelines that implement a pipeline template
[**ListUsingGET**](PipelineTemplatesControllerApi.md#ListUsingGET) | **Get** /pipelineTemplates | List pipeline templates.
[**ResolveTemplatesUsingGET**](PipelineTemplatesControllerApi.md#ResolveTemplatesUsingGET) | **Get** /pipelineTemplates/resolve | Resolve a pipeline template.
[**UpdateUsingPOST**](PipelineTemplatesControllerApi.md#UpdateUsingPOST) | **Post** /pipelineTemplates/{id} | Update a pipeline template.


# **CreateUsingPOST**
> CreateUsingPOST(ctx, pipelineTemplate)
Create a pipeline template.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **pipelineTemplate** | [**interface{}**](interface{}.md)| pipelineTemplate | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DeleteUsingDELETE**
> map[string]interface{} DeleteUsingDELETE(ctx, id, optional)
Delete a pipeline template.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **id** | **string**| id | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **id** | **string**| id | 
 **application** | **string**| application | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetUsingGET**
> map[string]interface{} GetUsingGET(ctx, id)
Get a pipeline template.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **id** | **string**| id | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ListPipelineTemplateDependentsUsingGET**
> []interface{} ListPipelineTemplateDependentsUsingGET(ctx, id, optional)
List all pipelines that implement a pipeline template

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **id** | **string**| id | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **id** | **string**| id | 
 **recursive** | **bool**| recursive | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ListUsingGET**
> []interface{} ListUsingGET(ctx, optional)
List pipeline templates.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **scopes** | [**[]string**](string.md)| scopes | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ResolveTemplatesUsingGET**
> map[string]interface{} ResolveTemplatesUsingGET(ctx, source, optional)
Resolve a pipeline template.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **source** | **string**| source | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **source** | **string**| source | 
 **executionId** | **string**| executionId | 
 **pipelineConfigId** | **string**| pipelineConfigId | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **UpdateUsingPOST**
> UpdateUsingPOST(ctx, id, pipelineTemplate, optional)
Update a pipeline template.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **id** | **string**| id | 
  **pipelineTemplate** | [**interface{}**](interface{}.md)| pipelineTemplate | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **id** | **string**| id | 
 **pipelineTemplate** | [**interface{}**](interface{}.md)| pipelineTemplate | 
 **skipPlanDependents** | **bool**| skipPlanDependents | [default to false]

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

