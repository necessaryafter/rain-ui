package com.rainframework.ui.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainframework.ui.protocol.validation.ContractValidator;
import com.rainframework.ui.protocol.validation.ValidationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Parametrized fixture-driven test for contract validation.
 * Loads all valid fixtures from schema/fixtures/valid/ and verifies parsing+validation succeeds.
 * Loads all invalid fixtures from schema/fixtures/invalid/ with paired .error.json files
 * and verifies they fail with the expected error code+path.
 * <p>
 * This test is RED/FAILING in Phase A — ContractParser and ContractValidator don't exist yet.
 * Specification for M1 Phase B implementation.
 */
public class FixtureValidationTest {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static List<Path> validFixtures;
    private static List<Path> invalidFixtures;

    @BeforeAll
    static void loadFixtures() throws IOException {
        final var projectRoot = System.getProperty("user.dir");
        final var fixturesDir = Paths.get(projectRoot, "schema", "fixtures");

        validFixtures = new ArrayList<>();
        final var validDir = fixturesDir.resolve("valid");
        if (Files.exists(validDir)) {
            try (final var stream = Files.list(validDir)) {
                validFixtures = stream
                        .filter(p -> p.toString().endsWith(".json"))
                        .collect(Collectors.toList());
            }
        }

        invalidFixtures = new ArrayList<>();
        final var invalidDir = fixturesDir.resolve("invalid");
        if (Files.exists(invalidDir)) {
            try (final var stream = Files.list(invalidDir)) {
                invalidFixtures = stream
                        .filter(p -> p.toString().endsWith(".json") && !p.toString().endsWith(".error.json"))
                        .collect(Collectors.toList());
            }
        }
    }

    @TestFactory
    @DisplayName("Valid fixtures should parse and validate successfully")
    Iterable<DynamicTest> testValidFixtures() {
        return validFixtures.stream()
                .map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
                    final var content = Files.readString(path, StandardCharsets.UTF_8);
                    final var parser = new ContractParser();
                    final var contract = parser.parse(content);
                    assertNotNull(contract);

                    final var validator = new ContractValidator();
                    final var result = validator.validate(contract);
                    assertTrue(result.isValid(), "Fixture " + path.getFileName() + " should be valid");
                }))
                .collect(Collectors.toList());
    }

    @TestFactory
    @DisplayName("Invalid fixtures should fail validation with expected error code")
    Iterable<DynamicTest> testInvalidFixtures() {
        return invalidFixtures.stream()
                .map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
                    final var errorPath = path.resolveSibling(
                            path.getFileName().toString().replace(".json", ".error.json"));

                    if (!Files.exists(errorPath)) {
                        fail("Missing .error.json for fixture: " + path.getFileName());
                    }

                    final var content = Files.readString(path, StandardCharsets.UTF_8);
                    final var expectedStr = Files.readString(errorPath, StandardCharsets.UTF_8);
                    @SuppressWarnings("unchecked")
                    final var expected = (java.util.Map<String, Object>) mapper.readValue(expectedStr, java.util.Map.class);

                    final var expectedCode = (String) expected.get("code");
                    final var expectedPath = (String) expected.get("path");

                    final var parser = new ContractParser();
                    try {
                        final var contract = parser.parse(content);
                        final var validator = new ContractValidator();
                        final var result = validator.validate(contract);
                        assertFalse(result.isValid(), "Fixture should be invalid: " + path.getFileName());
                        assertEquals(expectedCode, result.getError().getCode().name());
                        assertEquals(expectedPath, result.getError().getPath());
                    } catch (ParseException e) {
                        assertEquals(expectedCode, e.getErrorCode().name());
                        assertEquals(expectedPath, e.getPath());
                    }
                }))
                .collect(Collectors.toList());
    }
}
