package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.MusicRequest;
import mediaservice.dtos.responses.MusicResponse;
import mediaservice.mappers.MusicMapper;
import mediaservice.models.Music;
import mediaservice.repositories.MusicRepository;
import mediaservice.services.MusicService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MusicServiceImpl implements MusicService {

    private final MusicRepository musicRepository;
    private final MusicMapper musicMapper;

    @Override
    @Transactional
    public MusicResponse createMusic(MusicRequest request) {
        Music music = musicMapper.toEntity(request);
        Music savedMusic = musicRepository.save(music);
        return musicMapper.toResponse(savedMusic);
    }

    @Override
    @Transactional(readOnly = true)
    public MusicResponse getMusicById(String id) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Music not found with id: " + id));
        return musicMapper.toResponse(music);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MusicResponse> getAllMusic() {
        List<Music> musics = musicRepository.findAll();
        return musicMapper.toResponseList(musics);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MusicResponse> getAllMusic(Pageable pageable) {
        Page<Music> musics = musicRepository.findAll(pageable);
        return musics.map(musicMapper::toResponse);
    }

    @Override
    @Transactional
    public MusicResponse updateMusic(String id, MusicRequest request) {
        Music music = musicRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Music not found with id: " + id));
        musicMapper.updateEntity(request, music);
        Music updatedMusic = musicRepository.save(music);
        return musicMapper.toResponse(updatedMusic);
    }

    @Override
    @Transactional
    public void deleteMusic(String id) {
        if (!musicRepository.existsById(id)) {
            throw new RuntimeException("Music not found with id: " + id);
        }
        musicRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MusicResponse> searchMusicByTitle(String title) {
        List<Music> musics = musicRepository.findAll(); // TODO: Add custom query with search
        return musicMapper.toResponseList(musics);
    }
}

