package lk.techmart.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedUser implements Serializable {
    private Integer userId;
    private String username;
    private String email;
    private String roleName;
}