package mediaservice.services;

import mediaservice.dtos.requests.CategoryLinkRequest;
import mediaservice.dtos.responses.CategoryLinkResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CategoryLinkService {
    CategoryLinkResponse createCategoryLink(CategoryLinkRequest request);
    CategoryLinkResponse getCategoryLinkById(String id);
    List<CategoryLinkResponse> getAllCategoryLinks();
    Page<CategoryLinkResponse> getAllCategoryLinks(Pageable pageable);
    void deleteCategoryLink(String id);
    List<CategoryLinkResponse> getCategoryLinksByCategoryId(String categoryId);
    List<CategoryLinkResponse> getCategoryLinksByTargetId(String targetId);
}

