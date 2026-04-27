package org.openmetadata.service.apps.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.api.model.block.LayoutBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.schema.api.configuration.OpenMetadataBaseUrlConfiguration;
import org.openmetadata.schema.settings.Settings;
import org.openmetadata.schema.utils.JsonUtils;
import org.openmetadata.service.Entity;
import org.openmetadata.service.search.SearchRepository;
import org.openmetadata.service.security.policyevaluator.SubjectContext;
import jakarta.ws.rs.core.Response;

/**
 * Handles the /metadata search slash command.
 * Queries OpenMetadata's SearchRepository and formats results as Slack blocks.
 */
@Slf4j
public class SlackSearchHandler {

  private static final int MAX_RESULTS = 5;
  private static final int MAX_DESC_LENGTH = 150;

  private final SearchRepository searchRepository;

  public SlackSearchHandler() {
    this.searchRepository = Entity.getSearchRepository();
  }

  /**
   * Perform a search and return Slack blocks for the results.
   *
   * @param query the search term from the slash command
   * @return list of Slack LayoutBlocks ready to send
   */
  public List<LayoutBlock> search(String query) {
    List<LayoutBlock> blocks = new ArrayList<>();

    try {
      String indexName = "all";
      String searchTerm = query;

      String[] parts = query.split("\\s+", 2);
      if (parts.length == 2) {
        String possibleType = parts[0].toLowerCase();
        Set<String> validTypes = Set.of(
            "table", "topic", "dashboard", "pipeline", "mlmodel", "container", "glossary", "user", "team");
        if (validTypes.contains(possibleType)) {
          indexName = possibleType;
          searchTerm = parts[1];
        }
      }

      // Restrict search strictly to name and displayName using wildcard matching
      String fieldQuery = String.format("name:*%s* OR displayName:*%s*", searchTerm, searchTerm);
      SearchRequest request =
          new SearchRequest()
              .withQuery(fieldQuery)
              .withSize(MAX_RESULTS)
              .withIndex(searchRepository.getIndexOrAliasName(indexName))
              .withFetchSource(true);

      SubjectContext subjectContext = SubjectContext.getSubjectContext("admin");
      Response response = searchRepository.search(request, subjectContext);
      JsonNode root = JsonUtils.readTree((String) response.getEntity());
      JsonNode hits = root.path("hits").path("hits");

      if (hits.isMissingNode() || hits.isEmpty()) {
        blocks.add(SlackBlockBuilder.section("No results found for: `" + query + "`"));
        return blocks;
      }

      blocks.add(SlackBlockBuilder.header("Search Results for: " + query));
      blocks.add(SlackBlockBuilder.divider());

      for (int i = 0; i < hits.size(); i++) {
        JsonNode source = hits.get(i).path("_source");
        String name = source.path("name").asText("Unknown");
        String entityType = source.path("entityType").asText("asset");
        String fqn = source.path("fullyQualifiedName").asText("");
        String description =
            SlackBlockBuilder.truncate(
                source.path("description").asText("No description available."), MAX_DESC_LENGTH);

        String baseUrl = "";
        try {
          Settings settings = Entity.getSystemRepository().getOMBaseUrlConfigInternal();
          if (settings != null && settings.getConfigValue() != null) {
            OpenMetadataBaseUrlConfiguration baseUrlConfig =
                (OpenMetadataBaseUrlConfiguration) settings.getConfigValue();
            baseUrl = baseUrlConfig.getOpenMetadataUrl();
          }
        } catch (Exception e) {
          LOG.warn("Failed to get OpenMetadata Base URL for Slack result links", e);
        }

        String url = String.format("%s/%s/%s", baseUrl, entityType, fqn);
        String sectionText =
            String.format("*[%s]* *%s*\n%s",
                entityType.toUpperCase(),
                name,
                description);

        blocks.add(SlackBlockBuilder.sectionWithLinkButton(
            sectionText, "View", url, "view_entity_" + i));
      }

    } catch (Exception e) {
      LOG.error("Error performing Slack search for query: {}", query, e);
      blocks.add(SlackBlockBuilder.section("An error occurred while searching. Please try again."));
    }

    return blocks;
  }
}
