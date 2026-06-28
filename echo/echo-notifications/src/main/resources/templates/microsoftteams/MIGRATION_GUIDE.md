# Microsoft Teams Webhook Migration Guide

## Overview

Microsoft is retiring Office 365 Connectors for Microsoft Teams with a deadline of **March 31, 2026**. If you're using Microsoft Teams notifications in Spinnaker, you need to migrate from legacy Incoming Webhooks to Power Automate Workflows webhooks.

## Do I Need to Migrate?

**YES, if your webhook URLs look like:**
- `https://outlook.office.com/webhook/...`
- `https://*.office365.com/...`

**NO, if your webhook URLs look like:**
- `https://prod-XX.eastus.logic.azure.com:443/workflows/...` (already using Power Automate)

## Migration Steps

### Step 1: Create Power Automate Workflow

1. Open Microsoft Teams and navigate to the channel where you want to receive notifications
2. Click the **"..."** menu next to the channel name
3. Select **Workflows**
4. Search for "**Post to a channel when a webhook request is received**"
5. Click **Add workflow**
6. Configure the workflow:
   - Select the Team and Channel
   - Click **Add workflow**
7. Copy the webhook URL (it will look like: `https://prod-XX.eastus.logic.azure.com:443/workflows/.../triggers/manual/paths/invoke?...`)

**Important:** Keep this URL secure - anyone with this URL can post to your Teams channel.

### Step 2: Update Spinnaker Configuration

Update your webhook URLs in Spinnaker:

#### Option A: Update Application Notification Settings (UI)
1. In Spinnaker UI, go to your application
2. Navigate to **Config** → **Notifications**
3. Find your Microsoft Teams notifications
4. Replace the old webhook URL with the new Power Automate webhook URL
5. Test the notification

#### Option B: Update Halyard Configuration (if using Halyard)
If you've configured webhooks via Halyard or other configuration management:

```yaml
notifications:
  microsoftteams:
    enabled: true
    # No other global config changes needed
```

Individual notification preferences are stored per application in Front50.

### Step 3: Test Your Notifications

1. Trigger a test notification from Spinnaker
2. Verify the message appears in your Teams channel
3. Check that the Adaptive Card format displays correctly

### Step 4: Migrate All Webhooks

Repeat Steps 1-3 for each Teams channel that receives Spinnaker notifications.

## What Changed?

### Webhook URL Format

| Aspect | Legacy (Office 365 Connectors) | New (Power Automate Workflows) |
|--------|-------------------------------|--------------------------------|
| URL Format | `https://outlook.office.com/webhook/...` | `https://prod-XX.eastus.logic.azure.com:443/workflows/...` |
| Card Format Support | MessageCard & Adaptive Cards | Adaptive Cards (recommended), MessageCard (limited) |
| Interactive Elements | Supported in MessageCard | Only in Adaptive Cards |
| Custom Bot Identity | Supported | Not supported (shows Flow bot) |
| Expiration | March 31, 2026 | No planned expiration |

### Message Format

**Good News:** Spinnaker Echo now uses Adaptive Cards by default (as of this update), which is the recommended format for Power Automate Workflows.

**If you're using custom templates with MessageCard format:**
- MessageCard format is supported but has limitations
- Interactive HttpPOST actions will not work
- Messages will show the default Workflows bot icon
- Consider migrating custom templates to Adaptive Card format

## Adaptive Cards vs MessageCard

### Adaptive Cards (Recommended)
✅ Fully supported in Power Automate Workflows  
✅ Interactive elements work  
✅ Better visual design with color-coded containers  
✅ Responsive across devices  
✅ Future-proof  

### MessageCard (Legacy)
⚠️ Limited support in Power Automate Workflows  
❌ Interactive HttpPOST actions don't work  
❌ Cannot customize bot icon/name  
⚠️ May be deprecated in the future  

## Troubleshooting

### Issue: Messages not appearing in Teams

**Solution:**
1. Verify the webhook URL is correct and complete (including all query parameters)
2. Check that the workflow is enabled in Teams (Workflows → Your workflows)
3. Ensure the Teams channel still exists
4. Test the webhook with a simple curl command:
   ```bash
   curl -X POST -H "Content-Type: application/json" \
     -d '{"type":"message","attachments":[{"contentType":"application/vnd.microsoft.card.adaptive","content":{"type":"AdaptiveCard","version":"1.4","body":[{"type":"TextBlock","text":"Test message"}]}}]}' \
     "YOUR_WEBHOOK_URL"
   ```

### Issue: Old connectors still working

**Explanation:** Microsoft is rolling out the deprecation gradually. However, all connectors will stop working by March 31, 2026. Migrate proactively to avoid disruption.

### Issue: Messages look different

**Explanation:** The new Adaptive Card format has a different visual style than the old MessageCard format. This is expected and provides better visual hierarchy with color-coded containers (green for success, red for failure, blue for in-progress).

### Issue: Custom bot icon not showing

**Explanation:** Power Automate Workflows webhooks do not support custom bot identity. All messages will appear with the Workflows bot (Flow bot) icon. This is a platform limitation.

### Issue: Interactive buttons not working

**Solution:**
1. Ensure you're using Adaptive Card format (not MessageCard)
2. MessageCard interactive elements are not supported in Power Automate Workflows
3. For custom templates with interactive elements, use Adaptive Card `Action.OpenUrl` or `Action.Http`

## Timeline

- **July 2024:** Microsoft announced Office 365 Connectors retirement
- **October 2025:** Migration deadline extended to March 31, 2026
- **December 2025:** Power Automate Workflows feature parity improvements
- **March 31, 2026:** Office 365 Connectors fully retired
- **After March 31, 2026:** Legacy webhooks will stop working

## Additional Resources

- [Official Microsoft Announcement](https://devblogs.microsoft.com/microsoft365dev/retirement-of-office-365-connectors-within-microsoft-teams/)
- [Microsoft Support: Send messages using incoming webhooks](https://support.microsoft.com/en-us/office/send-messages-in-teams-using-incoming-webhooks-323660ec-12ca-40b1-a1d3-a3df47e808c4)
- [Adaptive Cards Documentation](https://docs.microsoft.com/en-us/microsoftteams/platform/task-modules-and-cards/cards/cards-reference#adaptive-card)
- [Adaptive Cards Designer](https://adaptivecards.io/designer/) - Test your custom templates

## Need Help?

If you encounter issues during migration:
1. Check the Spinnaker Echo logs for error messages
2. Verify webhook URL format and validity
3. Test the webhook directly using curl
4. Consult the Microsoft Teams admin center for workflow status
5. Open an issue in the Spinnaker GitHub repository

## Summary

**Required Actions:**
1. ✅ Create Power Automate Workflows webhooks for each Teams channel
2. ✅ Update webhook URLs in Spinnaker application notification settings
3. ✅ Test notifications in each channel
4. ✅ Complete migration before March 31, 2026

**Optional Actions:**
- Review and update custom templates to use Adaptive Cards
- Document new webhook URLs in your team's runbooks
- Set calendar reminders to verify webhooks remain functional

**No Code Changes Required:** The Spinnaker Echo update handles both webhook formats automatically.
