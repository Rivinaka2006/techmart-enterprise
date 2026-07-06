package lk.techmart.ejb;

import lk.techmart.core.dto.ProductCreateRequest;
import lk.techmart.core.entity.Brand;
import lk.techmart.core.entity.Category;
import lk.techmart.core.entity.Product;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lk.techmart.core.service.ProductService;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Stateless(name = "ProductSessionBean")
public class ProductSessionBean implements ProductService {


    @PersistenceContext(unitName = "TechMartPU")
    private EntityManager em;

    @Override
    public List<Product> getAllProducts() {

        List<Product> products = em.createQuery(
                "SELECT DISTINCT p FROM Product p JOIN FETCH p.brand JOIN FETCH p.category",
                Product.class
        ).getResultList();

        List<Object[]> quantities = em.createQuery(
                "SELECT ws.product.id, COALESCE(SUM(ws.quantity), 0) FROM WarehouseStock ws GROUP BY ws.product.id",
                Object[].class
        ).getResultList();

        Map<Integer, Integer> quantityMap = new java.util.HashMap<>();
        for (Object[] row : quantities) {
            quantityMap.put((Integer) row[0], ((Number) row[1]).intValue());
        }

        return products.stream()
                .map(product -> copyForJson(product, quantityMap.getOrDefault(product.getId(), 0)))
                .toList();
    }

    @Override
    public Product getProductById(Integer id) {
        if (id == null) return null;
        List<Product> products = em.createQuery(
                "SELECT p FROM Product p JOIN FETCH p.brand JOIN FETCH p.category WHERE p.id = :id",
                Product.class
        ).setParameter("id", id).getResultList();

        if (products.isEmpty()) return null;
        Product product = products.get(0);
        return copyForJson(product, getProductQuantity(id));
    }

    @Override
    public List<Product> getProductsByCategory(Integer categoryId) {
        List<Product> products = em.createQuery(
                        "SELECT p FROM Product p JOIN FETCH p.brand JOIN FETCH p.category WHERE p.category.id = :catId",
                        Product.class)
                .setParameter("catId", categoryId)
                .getResultList();

        List<Object[]> quantities = em.createQuery(
                "SELECT ws.product.id, COALESCE(SUM(ws.quantity), 0) FROM WarehouseStock ws WHERE ws.product.category.id = :catId GROUP BY ws.product.id",
                Object[].class
        ).setParameter("catId", categoryId).getResultList();

        Map<Integer, Integer> quantityMap = new java.util.HashMap<>();
        for (Object[] row : quantities) {
            quantityMap.put((Integer) row[0], ((Number) row[1]).intValue());
        }

        return products.stream()
                .map(product -> copyForJson(product, quantityMap.getOrDefault(product.getId(), 0)))
                .toList();
    }

    @Override
    public long countProducts() {
        return em.createQuery("SELECT COUNT(p) FROM Product p", Long.class)
                .getSingleResult();
    }

    @Override
    public List<Brand> getAllBrands() {
        return em.createQuery("SELECT b FROM Brand b ORDER BY b.brandName", Brand.class)
                .getResultList();
    }

    @Override
    public List<Category> getAllCategories() {
        return em.createQuery("SELECT c FROM Category c ORDER BY c.categoryName", Category.class)
                .getResultList();
    }

    @Override
    public Product createProduct(ProductCreateRequest request) {
        ProductData data = validateProductData(request);

        Product product = new Product();
        product.setName(data.name());
        product.setBrand(data.brand());
        product.setCategory(data.category());
        product.setPrice(data.price());
        product.setAttributes(normalizeAttributes(request.getAttributes()));
        product.setStatus(normalizeStatus(request.getStatus()));
        product.setCreatedAt(new Date());

        em.persist(product);
        em.flush();
        return copyForJson(product, 0);
    }

    @Override
    public Product updateProduct(Integer id, ProductCreateRequest request) {
        if (id == null) throw new IllegalArgumentException("Product id is required");
        Product product = em.find(Product.class, id);
        if (product == null) return null;

        ProductData data = validateProductData(request);
        product.setName(data.name());
        product.setBrand(data.brand());
        product.setCategory(data.category());
        product.setPrice(data.price());
        product.setAttributes(normalizeAttributes(request.getAttributes()));
        product.setStatus(normalizeStatus(request.getStatus()));

        Product updated = em.merge(product);
        em.flush();
        return copyForJson(updated, getProductQuantity(id));
    }

    private Product copyForJson(Product source, Integer quantity) {
        Product copy = new Product();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setPrice(source.getPrice());
        copy.setAttributes(source.getAttributes());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setStatus(source.getStatus());
        copy.setQuantity(quantity == null ? 0 : quantity);

        Brand sourceBrand = source.getBrand();
        if (sourceBrand != null) {
            Brand brand = new Brand();
            brand.setId(sourceBrand.getId());
            brand.setBrandName(sourceBrand.getBrandName());
            copy.setBrand(brand);
        }

        Category sourceCategory = source.getCategory();
        if (sourceCategory != null) {
            Category category = new Category();
            category.setId(sourceCategory.getId());
            category.setCategoryName(sourceCategory.getCategoryName());
            copy.setCategory(category);
        }

        return copy;
    }

    private ProductData validateProductData(ProductCreateRequest request) {
        if (request == null) throw new IllegalArgumentException("Product details are required");
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isBlank()) throw new IllegalArgumentException("Product name is required");
        if (request.getBrandId() == null) throw new IllegalArgumentException("Brand is required");
        if (request.getCategoryId() == null) throw new IllegalArgumentException("Category is required");
        BigDecimal price = request.getPrice();
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Price must be zero or greater");
        }

        Brand brand = em.find(Brand.class, request.getBrandId());
        if (brand == null) throw new IllegalArgumentException("Unknown brand: " + request.getBrandId());

        Category category = em.find(Category.class, request.getCategoryId());
        if (category == null) throw new IllegalArgumentException("Unknown category: " + request.getCategoryId());

        return new ProductData(name, brand, category, price);
    }

    private Integer getProductQuantity(Integer productId) {
        Number quantity = em.createQuery(
                "SELECT COALESCE(SUM(ws.quantity), 0) FROM WarehouseStock ws WHERE ws.product.id = :productId",
                Number.class
        ).setParameter("productId", productId).getSingleResult();
        return quantity.intValue();
    }

    private String normalizeAttributes(String attributes) {
        String value = attributes == null ? "" : attributes.trim();
        return value.isBlank() ? "{}" : value;
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "ACTIVE" : status.trim().toUpperCase();
        return switch (value) {
            case "ACTIVE", "INACTIVE", "OUT_OF_STOCK", "DISCONTINUED" -> value;
            default -> "ACTIVE";
        };
    }

    private record ProductData(String name, Brand brand, Category category, BigDecimal price) {
    }
}




