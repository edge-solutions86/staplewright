package io.github.rkaushikdev.staplewright;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.settings.Settings;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

class CatalogueTest {

    static class OddConflict extends ConflictError {

        static final String CODE = "odd_conflict";

        OddConflict(String message) {
            super(message);
        }

        @Override
        public String code() {
            return CODE;
        }
    }

    private static List<String> exportedNames() {
        return Staplewright.EXPORTS.stream().map(Class::getSimpleName).toList();
    }

    /** Every refusal class in the library's own code, found on the class path. */
    private static Set<String> refusalClassesOnTheClassPath() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(HaspError.class));
        return scanner.findCandidateComponents("io.github.rkaushikdev.staplewright").stream()
                .map(BeanDefinition::getBeanClassName)
                .filter(name -> !name.endsWith("Test") && !name.contains("Test$"))
                .collect(Collectors.toSet());
    }

    @Test
    void thePackageExportsTheNamesItPromises() {
        for (Class<?> exported : Staplewright.EXPORTS) {
            assertThat(Modifier.isPublic(exported.getModifiers()))
                    .as(exported.getName())
                    .isTrue();
            assertThat(exported.getPackageName()).startsWith("io.github.rkaushikdev.staplewright");
        }
    }

    @Test
    void theExportListIsSorted() {
        assertThat(exportedNames()).isSorted();
    }

    @Test
    void nothingIsExportedTwice() {
        assertThat(new HashSet<>(exportedNames())).hasSize(exportedNames().size());
    }

    @Test
    void aCallerCanParseABittingOffThePackage() {
        assertThat(Staplewright.EXPORTS).contains(Bitting.class);
        assertThat(Bitting.parse("442446").render()).isEqualTo("442446");
    }

    @Test
    void aCallerCanParseASymbolOffThePackage() {
        assertThat(Staplewright.EXPORTS).contains(KeySymbol.class);
        assertThat(KeySymbol.parse("AA1").level()).isEqualTo(3);
    }

    @Test
    void aCallerCanReadTheSettingsOffThePackage() {
        assertThat(Staplewright.EXPORTS).contains(Settings.class);
        assertThat(Settings.fromMapping(Map.of()).cutting().maxRun()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void theVersionIsAString() {
        assertThat(Staplewright.VERSION.chars().filter(at -> at == '.').count()).isEqualTo(2);
    }

    @Test
    void noTwoRefusalsShareACode() {
        List<String> found = Catalogue.CATALOGUE.stream().map(Refusal::code).toList();
        assertThat(new HashSet<>(found)).hasSize(found.size());
    }

    @Test
    void noTwoRefusalsShareAClass() {
        List<Class<? extends HaspError>> found =
                Catalogue.CATALOGUE.stream().map(Refusal::kind).toList();
        assertThat(new HashSet<>(found)).hasSize(found.size());
    }

    @Test
    void everyErrorClassIsInTheCatalogue() {
        Set<String> known =
                Catalogue.CATALOGUE.stream().map(item -> item.kind().getName()).collect(Collectors.toSet());
        Set<String> found = refusalClassesOnTheClassPath();
        // The scan has to find the library's own refusals, or it proves nothing.
        assertThat(found).contains(NotFoundError.class.getName(), PinningError.class.getName());
        assertThat(known).containsAll(found);
    }

    @Test
    void theCodesComeBackSorted() {
        assertThat(Catalogue.codes()).isSorted();
    }

    @Test
    void aRefusalIsFoundByItsOwnClass() {
        assertThat(Catalogue.refusalFor(new NotFoundError("gone")).kind()).isEqualTo(NotFoundError.class);
    }

    @Test
    void aRefusalFallsBackToItsParent() {
        assertThat(Catalogue.refusalFor(new OddConflict("odd")).kind()).isEqualTo(ConflictError.class);
    }

    @Test
    void aRefusalNobodyCataloguedStillGetsAStatus() {
        assertThat(Catalogue.statusFor(new OddConflict("odd"))).isEqualTo(409);
    }

    @Test
    void aBareRefusalGetsTheGeneralEntry() {
        assertThat(Catalogue.refusalFor(new HaspError("something")).kind()).isEqualTo(HaspError.class);
    }

    @Test
    void theStatusesAreTheOnesTheApiUses() {
        assertThat(Catalogue.statusFor(new NotFoundError("gone"))).isEqualTo(404);
        assertThat(Catalogue.statusFor(new ConflictError("no"))).isEqualTo(409);
        assertThat(Catalogue.statusFor(new InputError("no"))).isEqualTo(422);
        assertThat(Catalogue.statusFor(new CapacityError("no"))).isEqualTo(409);
        assertThat(Catalogue.statusFor(new BittingError("no"))).isEqualTo(400);
    }

    @Test
    void aCapacityRefusalIsWorthAnotherGo() {
        assertThat(Catalogue.worthRetrying(new CapacityError("not enough"))).isTrue();
    }

    @Test
    void aConflictIsNot() {
        assertThat(Catalogue.worthRetrying(new ConflictError("wrong state"))).isFalse();
    }

    @Test
    void theCatalogueIsKeyedByCode() {
        assertThat(Catalogue.BY_CODE.get("not_found").kind()).isEqualTo(NotFoundError.class);
    }

    @Test
    void aRefusalReadsBackAsOneLine() {
        String line = Catalogue.BY_CODE.get("not_found").describe();
        assertThat(line).startsWith("not_found").contains("404");
    }

    @Test
    void theSheetCountsTheCodes() {
        List<String> lines = Catalogue.catalogueSheet();
        assertThat(lines.get(0)).isEqualTo(Catalogue.CATALOGUE.size() + " refusal codes");
        assertThat(lines).hasSize(Catalogue.CATALOGUE.size() + 1);
    }
}
