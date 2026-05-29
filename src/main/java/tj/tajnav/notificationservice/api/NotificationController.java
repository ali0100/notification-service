package tj.tajnav.notificationservice.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import tj.tajnav.notificationservice.api.dto.NotificationRequest;
import tj.tajnav.notificationservice.api.dto.NotificationResponse;
import tj.tajnav.notificationservice.application.NotificationService;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationResponse submit(@Valid @RequestBody NotificationRequest request) {
        return notificationService.submit(request);
    }
}