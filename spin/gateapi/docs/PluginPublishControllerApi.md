# \PluginPublishControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**PublishPluginUsingPOST**](PluginPublishControllerApi.md#PublishPluginUsingPOST) | **Post** /plugins/publish/{pluginId}/{pluginVersion} | Publish a plugin binary and the plugin info metadata.


# **PublishPluginUsingPOST**
> PublishPluginUsingPOST(ctx, plugin, pluginId, pluginInfo, pluginVersion)
Publish a plugin binary and the plugin info metadata.

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **plugin** | ***os.File**| plugin | 
  **pluginId** | **string**| pluginId | 
  **pluginInfo** | [**interface{}**](.md)| pluginInfo | 
  **pluginVersion** | **string**| pluginVersion | 

### Return type

 (empty response body)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: multipart/form-data
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

