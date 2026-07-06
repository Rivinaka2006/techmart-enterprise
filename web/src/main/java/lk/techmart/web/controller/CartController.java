
package lk.techmart.web.controller;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import lk.techmart.core.dto.CartItem;
import lk.techmart.web.session.AuthSessionBean;
import lk.techmart.web.session.CartSessionBean;

import java.util.List;
import java.util.Map;

@Path("/cart")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CartController {

    @Inject
    private CartSessionBean cartSession;

    @Inject
    private AuthSessionBean authSession;

    private Response requireLogin() {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", "Please log in before using the cart"))
                .build();
    }

    @POST
    @Path("/items")
    public Response addItem(Map<String, Integer> body) {
        if (!authSession.isLoggedIn()) return requireLogin();
        try {
            cartSession.startCart(authSession.getUserId()); 
            cartSession.addItem(body.get("productId"), body.get("quantity"));
            return Response.ok(buildCartView()).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @PUT
    @Path("/items/{productId}")
    public Response updateQuantity(@PathParam("productId") Integer productId, Map<String, Integer> body) {
        if (!authSession.isLoggedIn()) return requireLogin();
        try {
            cartSession.updateQuantity(productId, body.get("quantity"));
            return Response.ok(buildCartView()).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/items/{productId}")
    public Response removeItem(@PathParam("productId") Integer productId) {
        if (!authSession.isLoggedIn()) return requireLogin();
        cartSession.removeItem(productId);
        return Response.ok(buildCartView()).build();
    }

    @GET
    public Response viewCart() {
        if (!authSession.isLoggedIn()) return requireLogin();
        return Response.ok(buildCartView()).build();
    }

    @POST
    @Path("/checkout")
    public Response checkout() {
        if (!authSession.isLoggedIn()) return requireLogin();
        try {
            return Response.ok(Map.of("message", cartSession.checkout())).build();
        } catch (IllegalStateException e) {
            return Response.status(Response.Status.CONFLICT).entity(Map.of("error", e.getMessage())).build();
        }
    }

    private Map<String, Object> buildCartView() {
        List<CartItem> items = cartSession.viewCart();
        return Map.of("items", items, "total", cartSession.getCartTotal());
    }
}