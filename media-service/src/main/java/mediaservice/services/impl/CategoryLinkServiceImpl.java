package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.CategoryLinkRequest;
import mediaservice.dtos.responses.CategoryLinkResponse;
import mediaservice.mappers.CategoryLinkMapper;
import mediaservice.models.CategoryLink;
import mediaservice.repositories.CategoryLinkRepository;
import mediaservice.services.CategoryLinkService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryLinkServiceImpl implements CategoryLinkService {

    private final CategoryLinkRepository categoryLinkRepository;
    private final CategoryLinkMapper categoryLinkMapper;

    @Override
    @Transactional
    public CategoryLinkResponse createCategoryLink(CategoryLinkRequest request) {
        CategoryLink categoryLink = categoryLinkMapper.toEntity(request);
        CategoryLink savedCategoryLink = categoryLinkRepository.save(categoryLink);
        return categoryLinkMapper.toResponse(savedCategoryLink);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryLinkResponse getCategoryLinkById(String id) {
        CategoryLink categoryLink = categoryLinkRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category link not found with id: " + id));
        return categoryLinkMapper.toResponse(categoryLink);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryLinkResponse> getAllCategoryLinks() {
        List<CategoryLink> categoryLinks = categoryLinkRepository.findAll();
        return categoryLinkMapper.toResponseList(categoryLinks);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryLinkResponse> getAllCategoryLinks(Pageable pageable) {
        Page<CategoryLink> categoryLinks = categoryLinkRepository.findAll(pageable);
        return categoryLinks.map(categoryLinkMapper::toResponse);
    }

    @Override
    @Transactional
    public void deleteCategoryLink(String id) {
        if (!categoryLinkRepository.existsById(id)) {
            throw new RuntimeException("Category link not found with id: " + id);
        }
        categoryLinkRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryLinkResponse> getCategoryLinksByCategoryId(String categoryId) {
        List<CategoryLink> categoryLinks = categoryLinkRepository.findAll(); // TODO: Add custom query
        return categoryLinkMapper.toResponseList(categoryLinks);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryLinkResponse> getCategoryLinksByTargetId(String targetId) {
        List<CategoryLink> categoryLinks = categoryLinkRepository.findAll(); // TODO: Add custom query
        return categoryLinkMapper.toResponseList(categoryLinks);
    }
}

