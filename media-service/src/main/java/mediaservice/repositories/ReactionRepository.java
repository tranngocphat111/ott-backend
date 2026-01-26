package mediaservice.repositories;

import mediaservice.models.Account;
import mediaservice.models.Reaction;
import mediaservice.models.enums.ReactionTargetType;
import mediaservice.models.enums.ReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReactionRepository extends JpaRepository<Reaction, String> {
    List<Reaction> findByTargetIdAndTargetType(String targetId, ReactionTargetType targetType);
    Optional<Reaction> findByAccountAndTargetIdAndTargetType(Account account, String targetId, ReactionTargetType targetType);
    Long countByTargetIdAndTargetTypeAndReactionType(String targetId, ReactionTargetType targetType, ReactionType reactionType);
    boolean existsByAccountAndTargetIdAndTargetType(Account account, String targetId, ReactionTargetType targetType);
}

