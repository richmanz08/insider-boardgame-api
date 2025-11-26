package com.insidergame.insider_api.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlayerDto {

    private String uuid;
    private String playerName;
    private boolean isHost;
    private boolean isReady;
    private String joinedAt;
}

