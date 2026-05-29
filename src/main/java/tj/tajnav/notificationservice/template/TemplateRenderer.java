package tj.tajnav.notificationservice.template;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tj.tajnav.notificationservice.domain.ChannelType;
import tj.tajnav.notificationservice.persistence.NotificationTemplateEntity;
import tj.tajnav.notificationservice.persistence.NotificationTemplateRepository;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    private final NotificationTemplateRepository templateRepository;

    public String render(String templateCode, ChannelType channelType, Map<String, Object> parameters) {
        NotificationTemplateEntity template = templateRepository
            .findByTemplateCodeAndChannelType(templateCode, channelType)
            .orElseThrow(() -> new TemplateNotFoundException(templateCode, channelType));

        return interpolate(template.getContent(), parameters == null ? Map.of() : parameters);
    }

    String interpolate(String content, Map<String, Object> params) {
        Matcher matcher = PLACEHOLDER.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = params.containsKey(key) ? String.valueOf(params.get(key)) : matcher.group(0);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}