package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.Catalogue;
import io.github.rkaushikdev.staplewright.Refusal;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** What the service itself says about itself: the refusals it can give and the settings it runs on. */
@RestController
@Tag(name = "service")
public class ServiceController {

    private final Work work;

    public ServiceController(Work work) {
        this.work = work;
    }

    /** Every refusal code, its status, what it means and whether to try again. */
    @GetMapping("/refusals")
    public Map<String, List<Map<String, Object>>> refusals() {
        return Map.of(
                "refusals",
                Catalogue.CATALOGUE.stream().map(ServiceController::shape).toList());
    }

    /** The settings this shop is running on, one line a group. */
    @GetMapping("/settings")
    public Map<String, List<String>> settings() {
        return Map.of("settings", work.settings().describe());
    }

    private static Map<String, Object> shape(Refusal item) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", item.code());
        out.put("status", item.status());
        out.put("meaning", item.meaning());
        out.put("retry", item.retry());
        return out;
    }
}
