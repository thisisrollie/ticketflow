package com.rolliedev.ticketflow.sla;

import com.rolliedev.ticketflow.service.SlaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlaScheduler {

    private final SlaService slaService;

    @Scheduled(fixedDelayString = "${app.sla.check-delay-ms:60000}")
    public void checkOverdueSlas() {
        Instant now = Instant.now();

        int responseBreaches = slaService.markOverdueFirstResponseSlasAsBreached(now);
        int resolutionBreaches = slaService.markOverdueResolutionSlasAsBreached(now);

        if (responseBreaches > 0) {
            log.info("SLA check marked {} first response SLA(s) as breached", responseBreaches);
        }

        if (resolutionBreaches > 0) {
            log.info("SLA check marked {} resolution SLA(s) as breached", resolutionBreaches);
        }
    }
}
