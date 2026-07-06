package lk.techmart.web.controller;

import jakarta.ejb.EJB;
import jakarta.enterprise.context.RequestScoped;
import lk.techmart.core.dto.ProductCreateRequest;
import lk.techmart.core.entity.Product;
import lk.techmart.core.service.ProductService;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequestScoped
public class ProductController {

    @EJB(beanName = "ProductSessionBean")
    private ProductService productService;

    @GET
    public Response getAllProducts() {

        try {
            List<Product> products = productService.getAllProducts();
            return Response.ok(products).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"" + e.getMessage() + "\"}").build();
        }
    }

    @GET
    @Path("/meta")
    public Response getProductMeta() {
        try {
            return Response.ok(Map.of(
                    "brands", productService.getAllBrands(),
                    "categories", productService.getAllCategories()
            )).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @POST
    public Response createProduct(ProductCreateRequest request) {
        try {
            Product product = productService.createProduct(request);
            return Response.status(Response.Status.CREATED).entity(product).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response updateProduct(@PathParam("id") Integer id, ProductCreateRequest request) {
        try {
            Product product = productService.updateProduct(id, request);
            if (product == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(Map.of("error", "Product not found"))
                        .build();
            }
            return Response.ok(product).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/count")
    public Response getProductCount() {
        try {
            return Response.ok(Map.of("totalProducts", productService.countProducts())).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    @GET
    @Path("/{id}")
    public Response getProductById(@PathParam("id") Integer id) {
        Product product = productService.getProductById(id);
        if (product == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("{\"message\":\"Product not found\"}").build();
        }
        return Response.ok(product).build();
    }
}
