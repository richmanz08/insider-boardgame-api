package com.insidergame.insider_api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSummaryDto {
    private String id;
    private String word; // usually not included in broadcast, but can be null
    private String startedAt; // ISO string
    private String endsAt; // ISO string
    private int durationSeconds;
    private boolean finished;
}

