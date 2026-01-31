package mediaservice.services;

import mediaservice.dtos.requests.MusicRequest;
import mediaservice.dtos.responses.MusicResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MusicService {
    MusicResponse createMusic(MusicRequest request);
    MusicResponse getMusicById(String id);
    List<MusicResponse> getAllMusic();
    Page<MusicResponse> getAllMusic(Pageable pageable);
    MusicResponse updateMusic(String id, MusicRequest request);
    void deleteMusic(String id);
    List<MusicResponse> searchMusicByTitle(String title);
}

