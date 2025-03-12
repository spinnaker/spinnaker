Spinnaker application <strong>${notification.source.application}</strong> was judged to <strong>continue</strong> by <strong>${notification.additionalContext.judgedBy}</strong>.
<br/><br/>
<#if (notification.additionalContext.message)??>
  ${notification.additionalContext.message}
<br/><br/>
</#if>
<#if (notification.additionalContext.judgmentInput)??>>
Judgment <strong>${notification.additionalContext.judgmentInput}</strong> was selected.
<br/><br/>
</#if>
For more details, please visit:
<br/><br/>
<a href="${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}">
${baseUrl}/#/applications/${notification.source.application}/executions/details/${notification.source.executionId}
</a>
