# \DeckPluginsControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetPluginAssetUsingGET**](DeckPluginsControllerApi.md#GetPluginAssetUsingGET) | **Get** /plugins/deck/{pluginId}/{pluginVersion}/{asset} | Retrieve a single plugin asset by version
[**GetPluginManifestUsingGET**](DeckPluginsControllerApi.md#GetPluginManifestUsingGET) | **Get** /plugins/deck/plugin-manifest.json | Retrieve a plugin manifest


# **GetPluginAssetUsingGET**
> string GetPluginAssetUsingGET(ctx, asset, pluginId, pluginVersion)
Retrieve a single plugin asset by version

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
  **asset** | **string**| asset | 
  **pluginId** | **string**| pluginId | 
  **pluginVersion** | **string**| pluginVersion | 

### Return type

**string**

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

# **GetPluginManifestUsingGET**
> []DeckPluginVersion GetPluginManifestUsingGET(ctx, )
Retrieve a plugin manifest

### Required Parameters
This endpoint does not need any parameter.

### Return type

[**[]DeckPluginVersion**](DeckPluginVersion.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

