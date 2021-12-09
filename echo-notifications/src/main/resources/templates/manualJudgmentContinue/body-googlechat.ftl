<#if (notification.additionalContext.execution.name)??>
  <#if (notification.additionalContext.execution.trigger.buildInfo.number)??>
Stage <i>${notification.additionalContext.stageName}</i> for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline build ${notification.additionalContext.execution.trigger.buildInfo.number} was <b>judged to continue by ${notification.additionalContext.judgedBy}</b>.
  <#else>
Stage <i>${notification.additionalContext.stageName}</i> for ${notification.source.application}'s ${notification.additionalContext.execution.name} pipeline was <b>judged to continue by ${notification.additionalContext.judgedBy}</b>.
  </#if>
<#else>
Stage <i>${notification.additionalContext.stageName}</i> for ${notification.source.application} was <b>judged to continue by ${notification.additionalContext.judgedBy}</b>.
</#if>

<#if (notification.additionalContext.message)??>
<u>Instructions:</u>
${markdownToHtml.convert(notification.additionalContext.message)}
</#if>
<#if (notification.additionalContext.judgmentInput)??>
Judgment '${htmlToText.convert(notification.additionalContext.judgmentInput)}' was selected.
</#if>

For more details, please visit:
<#if (notification.additionalContext.stageId)??>
  <#if (notification.additionalContext.restrictExecutionDuringTimeWindow)??>
<a href="${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}?refId=${notification.additionalContext.stageId}&step=1">${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}?refId=${notification.additionalContext.stageId}&step=1</a>
  <#else>
<a href="${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}?refId=${notification.additionalContext.stageId}">${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}?refId=${notification.additionalContext.stageId}</a>
  </#if>
<#else>
<a href="${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}">${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}</a>
</#if>
