package mediaservice.services;

import mediaservice.dtos.requests.NoteRequest;
import mediaservice.dtos.responses.NoteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NoteService {
    NoteResponse createNote(NoteRequest request);
    NoteResponse getNoteById(String id);
    List<NoteResponse> getAllNotes();
    Page<NoteResponse> getAllNotes(Pageable pageable);
    NoteResponse updateNote(String id, NoteRequest request);
    void deleteNote(String id);
    List<NoteResponse> getNotesByUserId(String userId);
}

