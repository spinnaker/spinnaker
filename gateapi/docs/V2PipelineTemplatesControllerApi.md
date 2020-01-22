# \V2PipelineTemplatesControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**CreateUsingPOST1**](V2PipelineTemplatesControllerApi.md#CreateUsingPOST1) | **Post** /v2/pipelineTemplates/create | (ALPHA) Create a pipeline template.
[**DeleteUsingDELETE1**](V2PipelineTemplatesControllerApi.md#DeleteUsingDELETE1) | **Delete** /v2/pipelineTemplates/{id} | Delete a pipeline template.
[**GetUsingGET2**](V2PipelineTemplatesControllerApi.md#GetUsingGET2) | **Get** /v2/pipelineTemplates/{id} | (ALPHA) Get a pipeline template.
[**ListPipelineTemplateDependentsUsingGET1**](V2PipelineTemplatesControllerApi.md#ListPipelineTemplateDependentsUsingGET1) | **Get** /v2/pipelineTemplates/{id}/dependents | (ALPHA) List all pipelines that implement a pipeline template
[**ListUsingGET1**](V2PipelineTemplatesControllerApi.md#ListUsingGET1) | **Get** /v2/pipelineTemplates | (ALPHA) List pipeline templates.
[**ListVersionsUsingGET**](V2PipelineTemplatesControllerApi.md#ListVersionsUsingGET) | **Get** /v2/pipelineTemplates/versions | List pipeline templates with versions
[**PlanUsingPOST**](V2PipelineTemplatesControllerApi.md#PlanUsingPOST) | **Post** /v2/pipelineTemplates/plan | (ALPHA) Plan a pipeline template configuration.
[**UpdateUsingPOST1**](V2PipelineTemplatesControllerApi.md#UpdateUsingPOST1) | **Post** /v2/pipelineTemplates/update/{id} | (ALPHA) Update a pipeline template.


# **CreateUsingPOST1**
> CreateUsingPOST1(ctx, pipelineTemplate, optional)
(ALPHA) Create a pipeline template.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **pipelineTemplate** | [**interface{}**](interface{}.md)| pipelineTemplate | 
 **optional** | **map[string]interface{}** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a map[string]interface{}.

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **pipelineTemplate** | [**interface{}**](interface{}.md)| pipelineTemplate | 
 **tag** | **string**| tag | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DeleteUsingDELETE1**
> map[string]interface{} DeleteUsingDELETE1(ctx, id, optional)
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
 **digest** | **string**| digest | 
 **tag** | **string**| tag | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetUsingGET2**
> map[string]interface{} GetUsingGET2(ctx, id, optional)
(ALPHA) Get a pipeline template.

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
 **digest** | **string**| digest | 
 **tag** | **string**| tag | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ListPipelineTemplateDependentsUsingGET1**
> []interface{} ListPipelineTemplateDependentsUsingGET1(ctx, id)
(ALPHA) List all pipelines that implement a pipeline template

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **id** | **string**| id | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **ListUsingGET1**
> []interface{} ListUsingGET1(ctx, optional)
(ALPHA) List pipeline templates.

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

# **ListVersionsUsingGET**
> interface{} ListVersionsUsingGET(ctx, optional)
List pipeline templates with versions

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

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **PlanUsingPOST**
> map[string]interface{} PlanUsingPOST(ctx, pipeline)
(ALPHA) Plan a pipeline template configuration.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for logging, tracing, authentication, etc.
  **pipeline** | [**interface{}**](interface{}.md)| pipeline | 

### Return type

[**map[string]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **UpdateUsingPOST1**
> UpdateUsingPOST1(ctx, id, pipelineTemplate, optional)
(ALPHA) Update a pipeline template.

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
 **tag** | **string**| tag | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

