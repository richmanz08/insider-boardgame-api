package com.insidergame.insider_api.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameSummary
{
    private String insiderUuid;
    private String insiderName;
    private String masterUuid;
    private String masterName;
    private String word;
    private Map<String, Integer> scores; // playerUuid -> score
}
