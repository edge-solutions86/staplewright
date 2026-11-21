package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.campus.Condition;
import io.github.rkaushikdev.staplewright.campus.Deficiency;
import io.github.rkaushikdev.staplewright.campus.Identification;
import io.github.rkaushikdev.staplewright.campus.Lockout;
import io.github.rkaushikdev.staplewright.campus.LockoutLog;
import io.github.rkaushikdev.staplewright.campus.Survey;
import io.github.rkaushikdev.staplewright.campus.SurveyLine;
import io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers;
import io.github.rkaushikdev.staplewright.persistence.models.LockoutRow;
import io.github.rkaushikdev.staplewright.persistence.models.SurveyLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.SurveyRow;
import io.github.rkaushikdev.staplewright.services.LockoutService;
import io.github.rkaushikdev.staplewright.services.SurveyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lockouts and surveys over http, for the tablet the surveyor carries.
 *
 * <p>The survey line endpoint is a put rather than a post, and that is the whole design. A surveyor
 * walking a corridor on a bad signal will send the same line twice, and a put that overwrites is
 * right both times where a post that creates would leave two readings of one door and no way to tell
 * which was later.
 *
 * <p>Lockouts go the other way. Each step is its own post because each step is a thing that happened
 * at a time somebody has to be able to point at later, and collapsing them into one patch would let a
 * call be closed without ever being attended.
 */
@RestController
@Tag(name = "doors")
public class DoorRoutes {

    private static final DateTimeFormatter TO_THE_SECOND =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss", Locale.ROOT);

    private final Work work;

    public DoorRoutes(Work work) {
        this.work = work;
    }

    /** A call coming in about a door somebody is locked out of. */
    public record NewLockout(
            @NotNull @Size(min = 1, max = 32) String opening,
            @NotNull @Size(min = 1, max = 32) String keyholder,
            @NotNull LocalDateTime calledAt) {}

    /** A technician arriving at a call. */
    public record Attendance(@NotNull @Size(min = 1, max = 128) String technician, @NotNull LocalDateTime attendedAt) {}

    /** The door opened, and how the person was known to be who they said. */
    public record Entry(@NotNull Identification identifiedBy, @NotNull LocalDateTime closedAt) {}

    /** A call closed without opening the door, with the reason written down. */
    public record Refusal(@NotNull LocalDateTime closedAt, @NotNull @Size(min = 1, max = 512) String note) {}

    /** A walk round a building starting. */
    public record NewSurvey(@NotNull @Size(min = 1, max = 32) String building, @NotNull LocalDate walkedOn) {}

    /** One door as the surveyor found it. */
    public record Line(
            @NotNull @Size(min = 1, max = 128) String surveyor,
            @NotNull Condition condition,
            List<Deficiency> deficiencies,
            @Size(max = 512) String note) {
        public Line {
            deficiencies = deficiencies == null ? List.of() : List.copyOf(deficiencies);
            note = note == null ? "" : note;
        }
    }

    /** A moment written to the second, with the fraction only when there is one. */
    static String moment(LocalDateTime when) {
        String text = when.format(TO_THE_SECOND);
        if (when.getNano() == 0) {
            return text;
        }
        return text + String.format(Locale.ROOT, ".%06d", when.getNano() / 1000);
    }

    /** One call as the tablet wants it, with the times already strings. */
    static Map<String, Object> lockoutShape(LockoutRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("number", row.getNumber());
        out.put("opening", row.getOpeningNumber());
        out.put("keyholder", row.getKeyholder());
        out.put("state", row.getState());
        out.put("technician", row.getTechnician());
        out.put("identified_by", row.getIdentifiedBy());
        out.put("called_at", moment(row.getCalledAt()));
        out.put("attended_at", row.getAttendedAt().map(DoorRoutes::moment).orElse(null));
        out.put("closed_at", row.getClosedAt().map(DoorRoutes::moment).orElse(null));
        return out;
    }

    /** One survey line, with the condition back in words. */
    static Map<String, Object> lineShape(SurveyLineRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("opening", row.getOpeningNumber());
        out.put("surveyor", row.getSurveyor());
        out.put("condition", Condition.fromValue(row.getCondition()).label());
        out.put(
                "deficiencies",
                FacilitiesMappers.deficienciesFromText(row.getDeficiencies()).stream()
                        .map(Deficiency::value)
                        .toList());
        out.put("note", row.getNote());
        return out;
    }

    private <T> T lockouts(Function<LockoutService, T> call) {
        return work.apply(session -> call.apply(new LockoutService(session)));
    }

    private <T> T surveys(Function<SurveyService, T> call) {
        return work.apply(session -> call.apply(new SurveyService(session)));
    }

    @PostMapping("/lockouts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> log(@Valid @RequestBody NewLockout body) {
        return lockouts(service -> lockoutShape(service.log(body.opening(), body.keyholder(), body.calledAt())));
    }

    @PostMapping("/lockouts/{number}/attendance")
    public Map<String, Object> attend(@PathVariable String number, @Valid @RequestBody Attendance body) {
        return lockouts(service -> lockoutShape(service.attend(number, body.technician(), body.attendedAt())));
    }

    @PostMapping("/lockouts/{number}/entry")
    public Map<String, Object> letIn(@PathVariable String number, @Valid @RequestBody Entry body) {
        return lockouts(service -> lockoutShape(service.openDoor(number, body.identifiedBy(), body.closedAt())));
    }

    @PostMapping("/lockouts/{number}/refusal")
    public Map<String, Object> refuse(@PathVariable String number, @Valid @RequestBody Refusal body) {
        return lockouts(service -> lockoutShape(service.refuse(number, body.closedAt(), body.note())));
    }

    @GetMapping("/lockouts")
    public Map<String, Object> openCalls() {
        return lockouts(service -> {
            LockoutLog book = service.logBook();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("open", book.openCalls().stream().map(Lockout::describe).toList());
            out.put("total", book.size());
            return out;
        });
    }

    @PostMapping("/surveys")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> start(@Valid @RequestBody NewSurvey body) {
        return surveys(service -> {
            SurveyRow row = service.start(body.building(), body.walkedOn());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("building", row.getBuildingCode());
            out.put("walked_on", row.getWalkedOn().toString());
            return out;
        });
    }

    @PutMapping("/surveys/{building}/{walkedOn}/{opening}")
    public Map<String, Object> record(
            @PathVariable String building,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate walkedOn,
            @PathVariable String opening,
            @Valid @RequestBody Line body) {
        return surveys(service -> lineShape(service.record(
                building, walkedOn, opening, body.surveyor(), body.condition(), body.deficiencies(), body.note())));
    }

    @GetMapping("/surveys/{building}/{walkedOn}")
    public Map<String, Object> read(
            @PathVariable String building,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate walkedOn) {
        return surveys(service -> {
            Survey survey = service.survey(building, walkedOn);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("building", survey.building());
            out.put("walked_on", survey.walkedOn().toString());
            out.put("doors", survey.size());
            out.put(
                    "blocking",
                    survey.blocking().stream().map(SurveyLine::opening).toList());
            out.put("conditions", survey.countsByCondition());
            return out;
        });
    }

    @GetMapping("/buildings/{building}/latest-survey")
    public Map<String, Object> latest(@PathVariable String building) {
        return surveys(service -> {
            Optional<Survey> found = service.latest(building);
            Map<String, Object> out = new LinkedHashMap<>();
            if (found.isEmpty()) {
                out.put("building", building);
                out.put("walked_on", null);
                out.put("doors", 0);
                return out;
            }
            Survey survey = found.get();
            out.put("building", survey.building());
            out.put("walked_on", survey.walkedOn().toString());
            out.put("doors", survey.size());
            out.put(
                    "worst",
                    survey.worstFirst().stream()
                            .limit(5)
                            .map(SurveyLine::opening)
                            .toList());
            return out;
        });
    }
}
