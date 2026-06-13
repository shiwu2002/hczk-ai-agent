package com.hczk.hczkaiagentserver.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String refreshToken;
    private UserDTO user;
}
