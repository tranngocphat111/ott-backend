package mediaservice.services.impl;

import lombok.extern.slf4j.Slf4j;
import mediaservice.models.Content;
import mediaservice.repositories.ContentRepository;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ContentServiceImpl {
    private ContentRepository contentRepository;

    public Content findByID(String id) {
        return contentRepository.findById(id).orElse(null);
    }


}