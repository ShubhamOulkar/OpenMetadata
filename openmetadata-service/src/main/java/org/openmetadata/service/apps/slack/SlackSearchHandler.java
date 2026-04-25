package org.openmetadata.service.apps.slack;

import com.fasterxml.jackson.databind.JsonNode;
import com.slack.api.model.block.LayoutBlock;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.openmetadata.schema.search.SearchRequest;
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

  private static final String OM_BASE_URL =
      "https://jubilant-funicular-rvv69g9ggjq35jp4-8585.app.github.dev";
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
      SearchRequest request =
          new SearchRequest()
              .withQuery(query)
              .withSize(MAX_RESULTS)
              .withIndex("all")
              .withFetchSource(true);

      SubjectContext subjectContext = SubjectContext.getSubjectContext("admin");
      Response response = searchRepository.search(request, subjectContext);
      JsonNode root = JsonUtils.valueToTree(response.getEntity());
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

        String sectionText =
            String.format("*[%s]* <%s/%s/%s|%s>\n%s",
                entityType.toUpperCase(),
                OM_BASE_URL,
                entityType,
                fqn,
                name,
                description);

        blocks.add(SlackBlockBuilder.section(sectionText));
      }

    } catch (Exception e) {
      LOG.error("Error performing Slack search for query: {}", query, e);
      blocks.add(SlackBlockBuilder.section("An error occurred while searching. Please try again."));
    }

    return blocks;
  }
}
