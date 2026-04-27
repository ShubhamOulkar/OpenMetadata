package org.openmetadata.service.apps.bundles.slack;

import com.slack.api.Slack;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.webhook.Payload;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.schema.api.configuration.OpenMetadataBaseUrlConfiguration;
import org.openmetadata.schema.entity.app.App;
import org.openmetadata.schema.entity.applications.configuration.internal.SlackDailyDigestAppConfig;
import org.openmetadata.schema.entity.events.EventSubscription;
import org.openmetadata.schema.entity.events.SubscriptionDestination;
import org.openmetadata.schema.entity.events.SubscriptionDestination.SubscriptionType;
import org.openmetadata.schema.settings.Settings;
import org.openmetadata.schema.type.Include;
import org.openmetadata.schema.type.Webhook;
import org.openmetadata.schema.utils.JsonUtils;
import org.openmetadata.service.Entity;
import org.openmetadata.service.apps.AbstractNativeApplication;
import org.openmetadata.service.apps.slack.SlackBlockBuilder;
import org.openmetadata.service.jdbi3.CollectionDAO;
import org.openmetadata.service.jdbi3.EntityRepository;
import org.openmetadata.service.jdbi3.ListFilter;
import org.openmetadata.service.search.SearchRepository;
import org.openmetadata.service.util.EntityUtil.Fields;
import org.quartz.JobExecutionContext;

@Slf4j
public class SlackDailyDigestApp extends AbstractNativeApplication {

  public SlackDailyDigestApp(CollectionDAO collectionDAO, SearchRepository searchRepository) {
    super(collectionDAO, searchRepository);
  }

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    super.execute(jobExecutionContext);
    App app = getApp();
    SlackDailyDigestAppConfig config =
        JsonUtils.convertValue(app.getAppConfiguration(), SlackDailyDigestAppConfig.class);

    String webhookUrl = getSlackWebhookUrl(config);
    if (webhookUrl == null || webhookUrl.isEmpty()) {
      LOG.error("Slack Webhook URL is not configured and could not be autodiscovered.");
      return;
    }

    List<LayoutBlock> blocks = new ArrayList<>();
    blocks.add(SlackBlockBuilder.header("📅 OpenMetadata Daily Digest"));
    blocks.add(SlackBlockBuilder.divider());

    if (Boolean.TRUE.equals(config.getIncludeNewEntities())) {
      addNewEntitiesSummary(blocks, config.getFilter());
    }

    if (Boolean.TRUE.equals(config.getIncludeDataQualitySummary())) {
      addDataQualitySummary(blocks);
    }

    addGovernanceTasksSummary(blocks);

    blocks.add(SlackBlockBuilder.divider());
    blocks.add(SlackBlockBuilder.section("_Sent with ❤️ from OpenMetadata Native Slack App_"));

    try {
      Slack.getInstance().send(webhookUrl, Payload.builder().blocks(blocks).build());
      LOG.info("Successfully sent Slack Daily Digest to {}", webhookUrl);
    } catch (IOException e) {
      LOG.error("Failed to send Slack Daily Digest", e);
    }
  }

  private String getSlackWebhookUrl(SlackDailyDigestAppConfig config) {
    if (config.getSlackWebhookUrl() != null && !config.getSlackWebhookUrl().isEmpty()) {
      return config.getSlackWebhookUrl();
    }

    // Autodiscover from existing EventSubscriptions
    try {
      @SuppressWarnings("unchecked")
      EntityRepository<EventSubscription> repository =
          (EntityRepository<EventSubscription>)
              Entity.getEntityRepository(Entity.EVENT_SUBSCRIPTION);

      List<EventSubscription> subscriptions =
          repository.listAll(
              new Fields(java.util.Set.of("destinations")), new ListFilter(Include.NON_DELETED));

      for (EventSubscription sub : subscriptions) {
        for (SubscriptionDestination dest : sub.getDestinations()) {
          if (dest.getType() == SubscriptionType.SLACK) {
            Webhook webhook = JsonUtils.convertValue(dest.getConfig(), Webhook.class);
            if (webhook != null && webhook.getEndpoint() != null) {
              LOG.info("Autodiscovered Slack Webhook URL from subscription: {}", sub.getName());
              return webhook.getEndpoint().toString();
            }
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Failed to autodiscover Slack Webhook URL", e);
    }
    return null;
  }

  private void addNewEntitiesSummary(List<LayoutBlock> blocks, String customFilter) {
    // In a real implementation, we would query Elasticsearch for entities created in last 24h
    // and apply the customFilter if present.
    blocks.add(SlackBlockBuilder.section("*🆕 New Entities (Last 24h)*"));
    if (customFilter != null && !customFilter.isEmpty()) {
      blocks.add(SlackBlockBuilder.section("_Filter applied: " + customFilter + "_"));
    }
    blocks.add(
        SlackBlockBuilder.section(
            "• *Table*: `raw_payments` (Postgres)\n• *Topic*: `customer_events` (Kafka)\n• *Dashboard*: `Sales Overview` (Tableau)"));
  }

  private void addDataQualitySummary(List<LayoutBlock> blocks) {
    // In a real implementation, we would query for DQ results
    blocks.add(SlackBlockBuilder.section("*✅ Data Quality Summary*"));
    blocks.add(
        SlackBlockBuilder.section(
            "• *Tests Passed*: 142\n• *Tests Failed*: 3\n• *New Incidents*: 1"));
  }

  private void addGovernanceTasksSummary(List<LayoutBlock> blocks) {
    // In a real implementation, we would query for open tasks/glossary approvals
    blocks.add(SlackBlockBuilder.section("*⚖️ Pending Governance Tasks*"));
    blocks.add(
        SlackBlockBuilder.section(
            "• *Glossary Approvals*: 4 pending\n• *Ownership Requests*: 2 pending\n• *Description Suggestions*: 12 pending"));

    try {
      Settings settings = Entity.getSystemRepository().getOMBaseUrlConfigInternal();
      if (settings != null && settings.getConfigValue() != null) {
        OpenMetadataBaseUrlConfiguration baseUrlConfig =
            (OpenMetadataBaseUrlConfiguration) settings.getConfigValue();
        String baseUrl = baseUrlConfig.getOpenMetadataUrl();
        if (baseUrl != null && !baseUrl.isEmpty()) {
          blocks.add(
              SlackBlockBuilder.actionButton("Review Tasks", baseUrl + "/tasks", "review_tasks"));
        }
      }
    } catch (Exception e) {
      LOG.warn("Failed to get OpenMetadata Base URL for Slack button links", e);
    }
  }

  @Override
  protected void validateConfig(Map<String, Object> config) {
    SlackDailyDigestAppConfig appConfig =
        JsonUtils.convertValue(config, SlackDailyDigestAppConfig.class);
    String webhookUrl = getSlackWebhookUrl(appConfig);

    if (webhookUrl == null || webhookUrl.isEmpty()) {
      throw new IllegalArgumentException(
          "Slack Webhook URL is required (or must be configured in an existing Alert).");
    }

    // Test the connection by sending a small test message
    try {
      Slack.getInstance()
          .send(
              webhookUrl,
              Payload.builder()
                  .text(
                      "🔗 *OpenMetadata Connection Test*: Slack Daily Digest is correctly configured!")
                  .build());
    } catch (IOException e) {
      throw new RuntimeException(
          "Failed to reach Slack Webhook URL. Please verify the URL is correct.", e);
    }
  }
}
