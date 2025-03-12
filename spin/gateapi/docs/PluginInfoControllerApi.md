# \PluginInfoControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**DeletePluginInfoUsingDELETE**](PluginInfoControllerApi.md#DeletePluginInfoUsingDELETE) | **Delete** /plugins/info/{id} | Delete plugin info with the provided Id
[**GetAllPluginInfoUsingGET**](PluginInfoControllerApi.md#GetAllPluginInfoUsingGET) | **Get** /plugins/info | Get all plugin info objects
[**PersistPluginInfoUsingPOST**](PluginInfoControllerApi.md#PersistPluginInfoUsingPOST) | **Post** /plugins/info | Persist plugin metadata information
[**PersistPluginInfoUsingPUT**](PluginInfoControllerApi.md#PersistPluginInfoUsingPUT) | **Put** /plugins/info | Persist plugin metadata information


# **DeletePluginInfoUsingDELETE**
> interface{} DeletePluginInfoUsingDELETE(ctx, id)
Delete plugin info with the provided Id

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

# **GetAllPluginInfoUsingGET**
> []interface{} GetAllPluginInfoUsingGET(ctx, optional)
Get all plugin info objects

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***PluginInfoControllerApiGetAllPluginInfoUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a PluginInfoControllerApiGetAllPluginInfoUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **service** | **optional.String**| service | 

### Return type

[**[]interface{}**](interface{}.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **PersistPluginInfoUsingPOST**
> PersistPluginInfoUsingPOST(ctx, pluginInfo)
Persist plugin metadata information

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **pluginInfo** | [**SpinnakerPluginInfo**](SpinnakerPluginInfo.md)| pluginInfo | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **PersistPluginInfoUsingPUT**
> PersistPluginInfoUsingPUT(ctx, pluginInfo)
Persist plugin metadata information

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **pluginInfo** | [**SpinnakerPluginInfo**](SpinnakerPluginInfo.md)| pluginInfo | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: application/json
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

