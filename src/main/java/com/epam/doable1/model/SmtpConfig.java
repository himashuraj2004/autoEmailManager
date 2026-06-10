package com.epam.doable1.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmtpConfig {

    private String host;
    private int port;
    private String username;
    private String password;
    private String fromEmail;
    private String ceoName;
    private String ceoEmail;
}
