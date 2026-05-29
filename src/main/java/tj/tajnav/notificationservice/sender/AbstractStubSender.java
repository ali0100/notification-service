package tj.tajnav.notificationservice.sender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ThreadLocalRandom;

@Slf4j
abstract class AbstractStubSender implements NotificationSender {

    @Value("${app.sender.failure-rate:0.1}")
    private double failureRate;

    @Value("${app.sender.min-delay-ms:50}")
    private long minDelayMs;

    @Value("${app.sender.max-delay-ms:150}")
    private long maxDelayMs;

    @Override
    public void send(String recipientId, String renderedContent) {
        simulateDelay();
        if (ThreadLocalRandom.current().nextDouble() < failureRate) {
            throw new SenderException("[%s] simulated failure for recipient %s".formatted(channel(), recipientId));
        }
        log.info("[{}] sent to {}: {}", channel(), recipientId, renderedContent);
    }

    private void simulateDelay() {
        try {
            long delay = minDelayMs + ThreadLocalRandom.current().nextLong(maxDelayMs - minDelayMs + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}