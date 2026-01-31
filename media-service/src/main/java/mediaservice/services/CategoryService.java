package mediaservice.services;

import mediaservice.dtos.requests.CategoryRequest;
import mediaservice.dtos.responses.CategoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CategoryService {
    CategoryResponse createCategory(CategoryRequest request);
    CategoryResponse getCategoryById(String id);
    List<CategoryResponse> getAllCategories();
    Page<CategoryResponse> getAllCategories(Pageable pageable);
    CategoryResponse updateCategory(String id, CategoryRequest request);
    void deleteCategory(String id);
    List<CategoryResponse> getActiveCategories();
}

