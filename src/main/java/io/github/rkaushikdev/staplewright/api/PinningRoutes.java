package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.pinning.CylinderPinning;
import io.github.rkaushikdev.staplewright.pinning.DriverProfile;
import io.github.rkaushikdev.staplewright.pinning.PinColumn;
import io.github.rkaushikdev.staplewright.pinning.PinKit;
import io.github.rkaushikdev.staplewright.pinning.PinSegment;
import io.github.rkaushikdev.staplewright.pinning.PinningSpec;
import io.github.rkaushikdev.staplewright.pinning.SegmentKind;
import io.github.rkaushikdev.staplewright.services.PinningService;
import io.github.rkaushikdev.staplewright.spec.Catalog;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bench work over http, which is the one part of this a phone actually reads.
 *
 * <p>The chart route gives back lines because that is what gets propped against the vice. The stack
 * route gives back numbers because that is what a second program checks the first one with. Both
 * come off the same pinning, so they cannot drift. Drawing brass is a post because it takes parts
 * out of a cabinet somebody else is drawing from too; it is all or nothing, so a kit that cannot fill
 * the order is a 409 naming the short bins, and the cabinet is untouched. The family comes off the
 * catalogue rather than off the request: a caller who could name a column height could pin a
 * cylinder that never reaches the shear line.
 */
@RestController
@RequestMapping("/pinning")
@Tag(name = "pinning")
public class PinningRoutes {

    /** What the cabinet a dry run draws from is called. */
    public static final String BENCH_KIT = "bench cabinet";

    public static final String DEFAULT_MAKER = "Schlage";
    public static final String DEFAULT_FAMILY = "C keyway six pin";

    private final Work work;

    public PinningRoutes(Work work) {
        this.work = work;
    }

    /** What a cabinet was stocked with, for a caller doing a dry run. */
    public record KitStock(
            @NotNull @Size(min = 1) List<Integer> bottoms,
            @NotNull @Size(min = 1) List<Integer> wafers,
            @NotNull @Size(min = 1) List<Integer> drivers,
            @Min(1) @Max(10000) Integer count) {
        public KitStock {
            count = count == null ? PinKit.STANDARD_COUNT : count;
        }
    }

    /** A draw against a cabinet for one group's cylinder. */
    public record DrawOrder(
            @NotNull @Size(min = 1, max = 64) String system,
            @NotNull @Size(min = 1, max = 64) String group,
            DriverProfile profile,
            @NotNull @Valid KitStock kit) {
        public DrawOrder {
            profile = profile == null ? DriverProfile.STANDARD : profile;
        }
    }

    /** Every chamber, floor first, in the order it gets loaded. */
    static List<Map<String, Object>> stackShape(CylinderPinning pinning) {
        List<Map<String, Object>> found = new ArrayList<>();
        for (int chamber = 1; chamber <= pinning.chambers(); chamber++) {
            PinColumn stack = pinning.stack(chamber);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("chamber", chamber);
            out.put("bottom", stack.bottomMils());
            out.put("wafers", stack.wafersMils());
            out.put("driver", stack.driverMils());
            out.put("profile", stack.driverProfile().value());
            out.put("breaks", stack.breakHeights());
            out.put("height", stack.heightMils());
            found.add(out);
        }
        return found;
    }

    private <T> T service(String maker, String family, Function<PinningService, T> call) {
        PinningSpec spec = Catalog.family(maker, family).pinning();
        return work.apply(session -> call.apply(new PinningService(session, spec)));
    }

    @GetMapping("/{system}/{group}")
    public Map<String, Object> stacks(
            @PathVariable String system,
            @PathVariable String group,
            @RequestParam(defaultValue = "standard") DriverProfile profile,
            @RequestParam(name = "manufacturer", defaultValue = DEFAULT_MAKER) String maker,
            @RequestParam(name = "family", defaultValue = DEFAULT_FAMILY) String family) {
        return service(maker, family, pins -> {
            CylinderPinning pinning = pins.pin(system, group, profile);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("system", system);
            out.put("group", group);
            out.put("chambers", pinning.chambers());
            out.put("wafers", pinning.waferCount());
            out.put("pieces", pinning.pieceCount());
            out.put("mastered", pinning.masteredChambers());
            out.put("operating_keys", pinning.operatingKeyCount());
            out.put("incidental_keys", pinning.incidentalKeyCount());
            out.put("stacks", stackShape(pinning));
            return out;
        });
    }

    @GetMapping("/{system}/{group}/chart")
    public ResponseEntity<?> chart(
            @PathVariable String system,
            @PathVariable String group,
            @RequestParam(defaultValue = "") String serial,
            @RequestParam(defaultValue = "false") boolean plain,
            @RequestParam(name = "manufacturer", defaultValue = DEFAULT_MAKER) String maker,
            @RequestParam(name = "family", defaultValue = DEFAULT_FAMILY) String family) {
        return Pages.page(service(maker, family, pins -> pins.chartFor(system, group, serial)), plain);
    }

    @GetMapping("/{system}/{group}/keys")
    public Map<String, Object> keys(
            @PathVariable String system,
            @PathVariable String group,
            @RequestParam(name = "manufacturer", defaultValue = DEFAULT_MAKER) String maker,
            @RequestParam(name = "family", defaultValue = DEFAULT_FAMILY) String family) {
        return service(maker, family, pins -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put(
                    "symbols",
                    pins.symbolsFor(group).stream().map(KeySymbol::render).toList());
            out.put(
                    "bittings",
                    pins.bittingsFor(system, group).stream()
                            .map(Bitting::render)
                            .toList());
            return out;
        });
    }

    @GetMapping("/groups/{group}/cylinders")
    public Map<String, Object> cylinders(
            @PathVariable String group,
            @RequestParam(name = "manufacturer", defaultValue = DEFAULT_MAKER) String maker,
            @RequestParam(name = "family", defaultValue = DEFAULT_FAMILY) String family) {
        return service(maker, family, pins -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("group", group);
            out.put(
                    "cylinders",
                    pins.cylindersFor(group).stream()
                            .map(row -> {
                                Map<String, Object> each = new LinkedHashMap<>();
                                each.put("serial", row.getSerial());
                                each.put("format", row.getFormat());
                                each.put("section", row.getSection());
                                each.put("chambers", row.getChambers());
                                each.put("installed_at", row.getInstalledAt().orElse(null));
                                return each;
                            })
                            .toList());
            return out;
        });
    }

    @PostMapping("/draw")
    public Map<String, Object> draw(
            @Valid @RequestBody DrawOrder body,
            @RequestParam(name = "manufacturer", defaultValue = DEFAULT_MAKER) String maker,
            @RequestParam(name = "family", defaultValue = DEFAULT_FAMILY) String family) {
        PinKit kit = PinKit.standard(
                BENCH_KIT,
                body.kit().bottoms(),
                body.kit().wafers(),
                body.kit().drivers(),
                body.kit().count());
        int before = kit.total();
        return service(maker, family, pins -> {
            PinningService.Drawn drawn = pins.drawFrom(kit, body.system(), body.group());
            List<PinSegment> parts = drawn.pinning().parts();
            Map<String, Integer> bins = new LinkedHashMap<>();
            for (SegmentKind kind : SegmentKind.values()) {
                bins.put(kind.value(), (int)
                        parts.stream().filter(part -> part.kind() == kind).count());
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("drawn", before - kit.total());
            out.put("left", kit.total());
            out.put("pieces", drawn.pinning().pieceCount());
            out.put("summary", drawn.summary());
            out.put("bins", bins);
            return out;
        });
    }
}
