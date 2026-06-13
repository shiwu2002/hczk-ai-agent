package com.hczk.hczkaiagentserver.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String role;
    private BigDecimal balance;
    private String phoneNumber;
    private String companyName;
}
