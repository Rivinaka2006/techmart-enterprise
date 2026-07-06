package lk.techmart.core.service;

import jakarta.ejb.Remote;
import lk.techmart.core.dto.ProductCreateRequest;
import lk.techmart.core.entity.Brand;
import lk.techmart.core.entity.Category;
import lk.techmart.core.entity.Product;
import java.util.List;

@Remote
public interface ProductService {
    List<Product> getAllProducts();
    Product getProductById(Integer id);
    List<Product> getProductsByCategory(Integer categoryId);
    long countProducts();
    List<Brand> getAllBrands();
    List<Category> getAllCategories();
    Product createProduct(ProductCreateRequest request);
    Product updateProduct(Integer id, ProductCreateRequest request);
}
