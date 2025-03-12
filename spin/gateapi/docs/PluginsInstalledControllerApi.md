# \PluginsInstalledControllerApi

All URIs are relative to *https://localhost*

Method | HTTP request | Description
------------- | ------------- | -------------
[**GetInstalledPluginsUsingGET**](PluginsInstalledControllerApi.md#GetInstalledPluginsUsingGET) | **Get** /plugins/installed | Get all installed Spinnaker plugins


# **GetInstalledPluginsUsingGET**
> map[string][]SpinnakerPluginDescriptor GetInstalledPluginsUsingGET(ctx, optional)
Get all installed Spinnaker plugins

### Required Parameters

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **ctx** | **context.Context** | context for authentication, logging, cancellation, deadlines, tracing, etc.
 **optional** | ***PluginsInstalledControllerApiGetInstalledPluginsUsingGETOpts** | optional parameters | nil if no parameters

### Optional Parameters
Optional parameters are passed through a pointer to a PluginsInstalledControllerApiGetInstalledPluginsUsingGETOpts struct

Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **service** | **optional.String**| service | 

### Return type

[**map[string][]SpinnakerPluginDescriptor**](array.md)

### Authorization

No authorization required

### HTTP request headers

 - **Content-Type**: Not defined
 - **Accept**: */*

[[Back to top]](#) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to Model list]](../README.md#documentation-for-models) [[Back to README]](../README.md)

