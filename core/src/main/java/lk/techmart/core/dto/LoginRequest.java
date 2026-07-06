package lk.techmart.core.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class LoginRequest implements Serializable {
    private String usernameOrEmail;
    private String password;
    private String ipAddress;
    private String userAgent;
}