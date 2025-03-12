# \V2CanaryConfigControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**CreateCanaryConfigUsingPOST**](V2CanaryConfigControllerApi.md#CreateCanaryConfigUsingPOST) | **Post** /v2/canaryConfig | Create a canary configuration
[**DeleteCanaryConfigUsingDELETE**](V2CanaryConfigControllerApi.md#DeleteCanaryConfigUsingDELETE) | **Delete** /v2/canaryConfig/{id} | Delete a canary configuration
[**GetCanaryConfigUsingGET**](V2CanaryConfigControllerApi.md#GetCanaryConfigUsingGET) | **Get** /v2/canaryConfig/{id} | Retrieve a canary configuration by id
[**GetCanaryConfigsUsingGET**](V2CanaryConfigControllerApi.md#GetCanaryConfigsUsingGET) | **Get** /v2/canaryConfig | Retrieve a list of canary configurations
[**UpdateCanaryConfigUsingPUT**](V2CanaryConfigControllerApi.md#UpdateCanaryConfigUsingPUT) | **Put** /v2/canaryConfig/{id} | Update a canary configuration


# **CreateCanaryConfigUsingPOST**
> interface{} CreateCanaryConfigUsingPOST(ctx, config, optional)
Create a canary configuration

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **config** | [**interface{}**](interface{}.md)| config | 
 **optional** | ***V2CanaryConfigControllerApiCreateCanaryConfigUsingPOSTOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a V2CanaryConfigControllerApiCreateCanaryConfigUsingPOSTOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **configurationAccountName** | **optional.String**| configurationAccountName | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **DeleteCanaryConfigUsingDELETE**
> DeleteCanaryConfigUsingDELETE(ctx, id, optional)
Delete a canary configuration

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 
 **optional** | ***V2CanaryConfigControllerApiDeleteCanaryConfigUsingDELETEOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a V2CanaryConfigControllerApiDeleteCanaryConfigUsingDELETEOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **configurationAccountName** | **optional.String**| configurationAccountName | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetCanaryConfigUsingGET**
> interface{} GetCanaryConfigUsingGET(ctx, id, optional)
Retrieve a canary configuration by id

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **id** | **string**| id | 
 **optional** | ***V2CanaryConfigControllerApiGetCanaryConfigUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a V2CanaryConfigControllerApiGetCanaryConfigUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------

 **configurationAccountName** | **optional.String**| configurationAccountName | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetCanaryConfigsUsingGET**
> []interface{} GetCanaryConfigsUsingGET(ctx, optional)
Retrieve a list of canary configurations

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***V2CanaryConfigControllerApiGetCanaryConfigsUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a V2CanaryConfigControllerApiGetCanaryConfigsUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **application** | **optional.String**| application | 
 **configurationAccountName** | **optional.String**| configurationAccountName | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **UpdateCanaryConfigUsingPUT**
> interface{} UpdateCanaryConfigUsingPUT(ctx, config, id, optional)
Update a canary configuration

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **config** | [**interface{}**](interface{}.md)| config | 
  **id** | **string**| id | 
 **optional** | ***V2CanaryConfigControllerApiUpdateCanaryConfigUsingPUTOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a V2CanaryConfigControllerApiUpdateCanaryConfigUsingPUTOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------


 **configurationAccountName** | **optional.String**| configurationAccountName | 

### Return type

[**interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

