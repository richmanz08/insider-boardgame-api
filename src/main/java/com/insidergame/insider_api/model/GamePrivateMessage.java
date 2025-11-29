package com.insidergame.insider_api.model;

import com.insidergame.insider_api.enums.RoleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamePrivateMessage {
    private String playerUuid;
    private RoleType role;
    private String word;
}
