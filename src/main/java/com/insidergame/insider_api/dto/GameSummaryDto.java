package com.insidergame.insider_api.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameSummaryDto {
    private String id;
    private String word;
    private String startedAt;
    private String endsAt;
    private int durationSeconds;
    private boolean finished;
    // Map of playerUuid -> opened
    private Map<String, Boolean> cardOpened;
}

