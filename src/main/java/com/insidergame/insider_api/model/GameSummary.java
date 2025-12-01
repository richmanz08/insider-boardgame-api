package com.insidergame.insider_api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSummary {
    // Player scores: playerUuid -> score
    private Map<String, Integer> scores;

    // Vote tally: targetUuid -> vote count
    private Map<String, Integer> voteTally;

    // Most voted player(s)
    private List<String> mostVoted;

    // Was INSIDER caught? (INSIDER is most voted)
    private boolean insiderCaught;

    // Did CITIZENS answer the word correctly?
    private boolean citizensAnsweredCorrectly;

    // INSIDER uuid
    private String insiderUuid;

    // MASTER uuid
    private String masterUuid;

    // The correct word
    private String word;
}
