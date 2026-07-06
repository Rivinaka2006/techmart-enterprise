
package lk.techmart.web.session;

import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Named;
import lk.techmart.core.dto.AuthenticatedUser;

import java.io.Serializable;

@Named
@SessionScoped
public class AuthSessionBean implements Serializable {

    private AuthenticatedUser currentUser;
    private Integer currentSessionId;

    public void login(AuthenticatedUser user) { this.currentUser = user; }
    public void logout() {
        this.currentUser = null;
        this.currentSessionId = null;
    }
    public boolean isLoggedIn() { return currentUser != null; }
    public AuthenticatedUser getCurrentUser() { return currentUser; }
    public Integer getUserId() { return currentUser != null ? currentUser.getUserId() : null; }
    public Integer getCurrentSessionId() { return currentSessionId; }
    public void setCurrentSessionId(Integer currentSessionId) { this.currentSessionId = currentSessionId; }
}
