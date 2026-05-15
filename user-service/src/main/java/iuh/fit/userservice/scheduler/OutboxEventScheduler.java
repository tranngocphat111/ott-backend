package iuh.fit.userservice.scheduler;

import iuh.fit.userservice.entity.OutboxEvent;
import iuh.fit.userservice.entity.enums.OutboxStatus;
import iuh.fit.userservice.repository.OutboxEventRepository;
import iuh.fit.userservice.service.OutboxEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxEventScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventService outboxEventService;

    @Scheduled(fixedDelay = 60000)
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository
                .findTop100ByStatusOrderByIdAsc(OutboxStatus.UNPUBLISHED);

        if (pending.isEmpty()) {
            return;
        }

        log.info("Outbox retry: {} pending events", pending.size());
        pending.forEach(outboxEventService::publishIfPending);
    }
}
