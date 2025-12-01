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
    private boolean isReady;
    private boolean isPlaying;
    private String joinedAt;
    private boolean isActive;
    private String lastActiveAt;

}
