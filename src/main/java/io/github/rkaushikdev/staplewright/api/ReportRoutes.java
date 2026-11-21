package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.control.NoticeLog;
import io.github.rkaushikdev.staplewright.control.Notices;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.persistence.Workspace;
import io.github.rkaushikdev.staplewright.reports.ControlPages;
import io.github.rkaushikdev.staplewright.reports.SchedulePages;
import io.github.rkaushikdev.staplewright.reports.StatisticsPages;
import io.github.rkaushikdev.staplewright.reports.Tally;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reports over http, all of them reads and all of them plain text.
 *
 * <p>Every one of these already exists as a list of lines, because the shop prints them. Handing the
 * same lines back over http means the web page and the piece of paper cannot disagree, which they did
 * for two years when the web version was built separately and rounded the deposits differently.
 *
 * <p>So the response is the lines, in order, and a content type of text when somebody asks for it
 * that way. There is no html here and there is not going to be any.
 */
@RestController
@RequestMapping("/reports")
@Tag(name = "reports")
public class ReportRoutes {

    private final Work work;

    public ReportRoutes(Work work) {
        this.work = work;
    }

    private <T> T space(Function<Workspace, T> call) {
        return work.apply(session -> call.apply(Workspace.forShop(work.settings(), session)));
    }

    @GetMapping("/systems/{name}/schedule")
    public ResponseEntity<?> schedule(
            @PathVariable String name,
            @RequestParam(defaultValue = "false") boolean plain,
            @RequestParam(defaultValue = "schedule") @Pattern(regexp = "^(schedule|bittings|tree)$") String shape) {
        List<String> lines = space(loaded -> {
            KeyingSchedule drawn = loaded.schedule(name);
            return switch (shape) {
                case "bittings" -> SchedulePages.bittingList(drawn);
                case "tree" -> SchedulePages.treeReport(drawn);
                default -> SchedulePages.scheduleReport(drawn, name);
            };
        });
        return Pages.page(lines, plain);
    }

    @GetMapping("/keyholders/{identifier}/statement")
    public ResponseEntity<?> statement(
            @PathVariable String identifier,
            @RequestParam Optional<LocalDate> on,
            @RequestParam(defaultValue = "false") boolean plain) {
        List<String> lines = space(loaded -> ControlPages.keyholderStatement(
                loaded.directory().person(identifier), loaded.ledger(), Optional.of(loaded.deposits()), on));
        return Pages.page(lines, plain);
    }

    @GetMapping("/keyholders/{identifier}/deposits")
    public ResponseEntity<?> deposits(
            @PathVariable String identifier, @RequestParam(defaultValue = "false") boolean plain) {
        return Pages.page(space(loaded -> ControlPages.depositStatement(loaded.deposits(), identifier)), plain);
    }

    @GetMapping("/departments/{department}")
    public ResponseEntity<?> department(
            @PathVariable String department,
            @RequestParam LocalDate on,
            @RequestParam(defaultValue = "false") boolean plain) {
        List<String> lines =
                space(loaded -> ControlPages.departmentSummary(loaded.directory(), loaded.ledger(), department, on));
        return Pages.page(lines, plain);
    }

    @GetMapping("/overdue")
    public ResponseEntity<?> overdue(@RequestParam LocalDate on, @RequestParam(defaultValue = "false") boolean plain) {
        return Pages.page(space(loaded -> ControlPages.overdueReport(loaded.ledger(), on)), plain);
    }

    @GetMapping("/letters")
    public ResponseEntity<?> letters(@RequestParam LocalDate on, @RequestParam(defaultValue = "false") boolean plain) {
        List<String> lines = space(loaded -> ControlPages.noticeRun(Notices.noticesDue(
                loaded.ledger(), on, new NoticeLog(), work.settings().notices())));
        return Pages.page(lines, plain);
    }

    @GetMapping("/campus")
    public ResponseEntity<?> campus(@RequestParam(defaultValue = "false") boolean plain) {
        List<String> lines = space(loaded -> StatisticsPages.summary(StatisticsPages.campusNumbers(
                loaded.openings(), loaded.coverage(), loaded.ledger(), loaded.deposits())));
        return Pages.page(lines, plain);
    }

    @GetMapping("/exposure")
    public Map<String, Object> widest(@RequestParam(defaultValue = "5") @Min(1) @Max(50) int limit) {
        List<Tally> found = space(loaded -> StatisticsPages.exposure(loaded.coverage(), loaded.ledger(), limit));
        List<Map<String, Object>> keys = found.stream()
                .map(tally -> {
                    Map<String, Object> each = new LinkedHashMap<>();
                    each.put("symbol", tally.name());
                    each.put("openings", tally.count());
                    return each;
                })
                .toList();
        return Map.of("keys", keys);
    }

    @GetMapping("/buildings")
    public Map<String, Object> buildings() {
        return Map.of(
                "keys_by_building",
                space(loaded -> StatisticsPages.keysByBuilding(loaded.coverage(), loaded.ledger())));
    }
}
