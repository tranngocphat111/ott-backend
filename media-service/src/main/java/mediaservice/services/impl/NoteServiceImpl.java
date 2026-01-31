package mediaservice.services.impl;

import lombok.RequiredArgsConstructor;
import mediaservice.dtos.requests.NoteRequest;
import mediaservice.dtos.responses.NoteResponse;
import mediaservice.mappers.NoteMapper;
import mediaservice.models.Note;
import mediaservice.repositories.NoteRepository;
import mediaservice.services.NoteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteServiceImpl implements NoteService {

    private final NoteRepository noteRepository;
    private final NoteMapper noteMapper;

    @Override
    @Transactional
    public NoteResponse createNote(NoteRequest request) {
        Note note = noteMapper.toEntity(request);
        Note savedNote = noteRepository.save(note);
        return noteMapper.toResponse(savedNote);
    }

    @Override
    @Transactional(readOnly = true)
    public NoteResponse getNoteById(String id) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Note not found with id: " + id));
        return noteMapper.toResponse(note);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoteResponse> getAllNotes() {
        List<Note> notes = noteRepository.findAll();
        return noteMapper.toResponseList(notes);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<NoteResponse> getAllNotes(Pageable pageable) {
        Page<Note> notes = noteRepository.findAll(pageable);
        return notes.map(noteMapper::toResponse);
    }

    @Override
    @Transactional
    public NoteResponse updateNote(String id, NoteRequest request) {
        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Note not found with id: " + id));
        noteMapper.updateEntity(request, note);
        Note updatedNote = noteRepository.save(note);
        return noteMapper.toResponse(updatedNote);
    }

    @Override
    @Transactional
    public void deleteNote(String id) {
        if (!noteRepository.existsById(id)) {
            throw new RuntimeException("Note not found with id: " + id);
        }
        noteRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoteResponse> getNotesByUserId(String userId) {
        List<Note> notes = noteRepository.findAll(); // TODO: Add custom query
        return noteMapper.toResponseList(notes);
    }
}

