package lk.techmart.core.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class RegisterRequest implements Serializable {
    private String username;
    private String email;
    private String password;
    private String ipAddress;
    private String userAgent;
}