package lk.techmart.web.controller;

import jakarta.ejb.EJB;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lk.techmart.core.dto.AuthenticatedUser;
import lk.techmart.core.dto.LoginRequest;
import lk.techmart.core.dto.RegisterRequest;
import lk.techmart.core.exception.DuplicateUserException;
import lk.techmart.core.service.SessionQueryService;
import lk.techmart.core.service.UserAuthService;
import lk.techmart.web.session.AuthSessionBean;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthController {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";

    @EJB
    private UserAuthService userAuthService;

    @EJB
    private SessionQueryService sessionQueryService;

    @Inject
    private AuthSessionBean authSession;

    @POST
    @Path("/register")
    public Response register(RegisterRequest req) {
        try {
            if (req == null) {
                return badRequest("Registration details are required");
            }

            AuthenticatedUser user = userAuthService.register(req.getUsername(), req.getEmail(), req.getPassword());
            authSession.login(user);
            authSession.setCurrentSessionId(sessionQueryService.startSession(user.getUserId()));
            return Response.ok(user).build();
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (DuplicateUserException e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (IllegalStateException e) {
            log.error("Registration configuration error", e);
            return serverError("Registration is not configured correctly");
        } catch (Exception e) {
            log.error("Unexpected registration error", e);
            return serverError("Unable to register user");
        }
    }

    @POST
    @Path("/login")
    public Response login(LoginRequest req) {
        try {
            if (req == null) {
                return badRequest("Login details are required");
            }

            AuthenticatedUser user = userAuthService.authenticate(req.getUsernameOrEmail(), req.getPassword());
            if (user == null) {
                return invalidCredentials();
            }

            authSession.login(user);
            authSession.setCurrentSessionId(sessionQueryService.startSession(user.getUserId()));
            return Response.ok(user).build();
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected login error", e);
            return serverError("Unable to log in");
        }
    }

    @POST
    @Path("/logout")
    public Response logout() {
        try {
            sessionQueryService.endSession(authSession.getCurrentSessionId());
            authSession.logout();
            return Response.ok(Map.of("message", "Logged out")).build();
        } catch (Exception e) {
            log.error("Unexpected logout error", e);
            return serverError("Unable to log out");
        }
    }

    @GET
    @Path("/me")
    public Response me() {
        if (!authSession.isLoggedIn()) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "Not logged in"))
                    .build();
        }
        return Response.ok(authSession.getCurrentUser()).build();
    }

    private Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", message == null || message.isBlank() ? "Invalid request" : message))
                .build();
    }

    private Response invalidCredentials() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", INVALID_CREDENTIALS_MESSAGE))
                .build();
    }

    private Response serverError(String message) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", message))
                .build();
    }
}
