package tj.tajnav.notificationservice.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tj.tajnav.notificationservice.domain.ChannelType;
import tj.tajnav.notificationservice.persistence.NotificationTemplateRepository;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TemplateRendererTest {

    @Mock
    NotificationTemplateRepository templateRepository;

    @InjectMocks
    TemplateRenderer renderer;

    @Test
    void interpolate_replacesAllPlaceholders() {
        String result = renderer.interpolate("Hello {{name}}, order {{orderId}}!", Map.of("name", "Alice", "orderId", "42"));
        assertThat(result).isEqualTo("Hello Alice, order 42!");
    }

    @Test
    void interpolate_leavesUnknownPlaceholdersIntact() {
        String result = renderer.interpolate("Hello {{name}}!", Map.of());
        assertThat(result).isEqualTo("Hello {{name}}!");
    }

    @Test
    void interpolate_emptyParameters() {
        String result = renderer.interpolate("No placeholders.", Map.of());
        assertThat(result).isEqualTo("No placeholders.");
    }

    @Test
    void render_throwsWhenTemplateNotFound() {
        when(templateRepository.findByTemplateCodeAndChannelType("MISSING", ChannelType.EMAIL))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> renderer.render("MISSING", ChannelType.EMAIL, Map.of()))
            .isInstanceOf(TemplateNotFoundException.class);
    }
}