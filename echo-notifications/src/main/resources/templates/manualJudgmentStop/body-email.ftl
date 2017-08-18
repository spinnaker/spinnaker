<#if (notification.additionalContext.message)??>
${htmlToText.convert(notification.additionalContext.message)}
</#if>

For more details, please visit:
<#if (notification.additionalContext.stageId)??>
  <#if (notification.additionalContext.restrictExecutionDuringTimeWindow)??>
${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}?refId=${notification.additionalContext.stageId}&step=1
  <#else>
${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}?refId=${notification.additionalContext.stageId}
  </#if>
<#else>
${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}
</#if>
