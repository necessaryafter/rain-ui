package com.rainframework.ui.protocol.validation;

import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.ContractParser;
import com.rainframework.ui.protocol.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PropertiesValidatorTest {
    private final PropertiesValidator validator = new PropertiesValidator();

    // One page of the GTS as the plugin's adapter would build it from GtsListing.
    private static final String GTS_PAGE = """
            {
              "pageLabel": "1/5",
              "isFirstPage": true,
              "isLastPage": false,
              "listings": [
                {
                  "id": "a1",
                  "pokemon": "AQIDBAUGBwg=",
                  "pokemonName": "Pikachu",
                  "sellerName": "Ash",
                  "saleType": "FIXED_PRICE",
                  "status": "ACTIVE",
                  "statusLabel": "Ativo",
                  "price": 1250.5,
                  "priceLabel": "1.250,50 ₽",
                  "timeLeft": "expira em 3h",
                  "bidData": { "bidCount": 0 }
                },
                {
                  "id": "a2",
                  "pokemon": "CQoLDA==",
                  "pokemonName": "Eevee",
                  "sellerName": "Misty",
                  "saleType": "AUCTION",
                  "status": "AWAITING_CLAIM",
                  "statusLabel": "Aguardando retirada",
                  "timeLeft": "encerrado",
                  "bidData": {
                    "currentBid": 900,
                    "currentBidLabel": "900 ₽",
                    "startingBidLabel": "500 ₽"
                  },
                  "awaitingClaimLabel": "Inventário cheio"
                }
              ]
            }
            """;

    @Test
    void acceptsARealGtsPage() throws Exception {
        assertValid(validator.validate(contract("gts"), GTS_PAGE));
    }

    @Test
    void acceptsAbsentAndNullOptionalValues() throws Exception {
        final var contract = contract("optional-bindings");

        assertValid(validator.validate(contract, "{}"));
        assertValid(validator.validate(contract, """
                { "subtitle": null, "tint": null, "icon": null, "locked": null, "entries": null }
                """));
    }

    @Test
    void acceptsAbsentValuesThatHaveADefault() throws Exception {
        assertValid(validator.validate(contract("defaults"), """
                { "listings": [ {}, { "label": "x" } ] }
                """));
    }

    @Test
    void rejectsAnAbsentRequiredValue() throws Exception {
        final var page = GTS_PAGE.replace("\"pageLabel\": \"1/5\",", "");

        assertFailure(
                validator.validate(contract("gts"), page),
                ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH,
                "pageLabel");
    }

    @Test
    void rejectsANullRequiredValue() throws Exception {
        final var page = GTS_PAGE.replace("\"pageLabel\": \"1/5\"", "\"pageLabel\": null");

        assertFailure(
                validator.validate(contract("gts"), page),
                ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH,
                "pageLabel");
    }

    @Test
    void rejectsAnAbsentRequiredFieldInsideAListItem() throws Exception {
        final var page = GTS_PAGE.replace("\"sellerName\": \"Misty\",", "");

        assertFailure(
                validator.validate(contract("gts"), page),
                ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH,
                "listings[1].sellerName");
    }

    @Test
    void rejectsAnUndeclaredKeyAtTheRoot() throws Exception {
        final var page = GTS_PAGE.replace("\"pageLabel\": \"1/5\",", "\"pageLabel\": \"1/5\", \"sellerUuid\": \"x\",");

        assertFailure(
                validator.validate(contract("gts"), page),
                ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH,
                "sellerUuid");
    }

    @Test
    void rejectsAnUndeclaredKeyInsideAListItem() throws Exception {
        final var page = GTS_PAGE.replace("\"id\": \"a1\",", "\"id\": \"a1\", \"sellerId\": \"uuid\",");

        assertFailure(
                validator.validate(contract("gts"), page),
                ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH,
                "listings[0].sellerId");
    }

    @Test
    void rejectsAnUndeclaredKeyInsideANestedObject() throws Exception {
        final var page = GTS_PAGE.replace("\"bidCount\": 0", "\"bidCount\": 0, \"bids\": []");

        assertFailure(
                validator.validate(contract("gts"), page),
                ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH,
                "listings[0].bidData.bids");
    }

    @Test
    void rejectsValuesOfTheWrongType() throws Exception {
        final var numbers = contract("text-number");

        assertFailure(validator.validate(numbers, """
                { "count": "3", "total": 1 }
                """), ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH, "count");
        assertFailure(validator.validate(numbers, """
                { "count": 1.5, "total": 1 }
                """), ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH, "count");
        assertFailure(validator.validate(contract("gts"), GTS_PAGE.replace("\"Pikachu\"", "true")),
                ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH, "listings[0].pokemonName");
        assertFailure(validator.validate(contract("defaults"), """
                { "listings": { "label": "x" } }
                """), ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH, "listings");
    }

    @Test
    void checksIntAndLongRanges() throws Exception {
        final var numbers = contract("text-number");

        assertFailure(validator.validate(numbers, """
                { "count": 2147483648, "total": 1 }
                """), ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH, "count");
        assertValid(validator.validate(numbers, """
                { "count": 2147483647, "total": 2147483648 }
                """));
        assertFailure(validator.validate(numbers, """
                { "count": 1, "total": 9223372036854775808 }
                """), ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH, "total");
    }

    @Test
    void acceptsAStringAtTheLengthLimit() throws Exception {
        final var page = GTS_PAGE.replace("\"Pikachu\"", "\"" + "a".repeat(4096) + "\"");

        assertValid(validator.validate(contract("gts"), page));
    }

    @Test
    void rejectsAStringOverTheLengthLimit() throws Exception {
        final var page = GTS_PAGE.replace("\"Pikachu\"", "\"" + "a".repeat(4097) + "\"");

        assertFailure(
                validator.validate(contract("gts"), page),
                ValidationErrorCode.LIMIT_EXCEEDED,
                "listings[0].pokemonName");
    }

    @Test
    void acceptsADeclaredAssetName() throws Exception {
        assertValid(validator.validate(contract("assets-named"), """
                { "icon": "fire", "badge": "grass" }
                """));
    }

    @Test
    void rejectsAnUndeclaredAssetName() throws Exception {
        assertFailure(validator.validate(contract("assets-named"), """
                { "icon": "lava" }
                """), ValidationErrorCode.UNDECLARED_ASSET, "icon");
    }

    @Test
    void rejectsAnAssetGivenAsAHashInsteadOfAName() throws Exception {
        assertFailure(validator.validate(contract("assets-named"), """
                { "icon": "b2ee0ef0cf4fd36e0d0426ec06dbe1db77735997c628f1aafd6eb4c14b0b567a" }
                """), ValidationErrorCode.UNDECLARED_ASSET, "icon");
    }

    @Test
    void rejectsAnItemThatIsNotBase64() throws Exception {
        final var page = GTS_PAGE.replace("\"AQIDBAUGBwg=\"", "\"not base64!!\"");

        assertFailure(
                validator.validate(contract("gts"), page),
                ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH,
                "listings[0].pokemon");
    }

    @Test
    void acceptsAnItemAtTheSizeLimit() throws Exception {
        final var page = GTS_PAGE.replace("\"AQIDBAUGBwg=\"", "\"" + "A".repeat(32 * 1024) + "\"");

        assertValid(validator.validate(contract("gts"), page));
    }

    @Test
    void rejectsAnItemOverTheSizeLimit() throws Exception {
        final var page = GTS_PAGE.replace("\"AQIDBAUGBwg=\"", "\"" + "A".repeat(32 * 1024 + 4) + "\"");

        assertFailure(
                validator.validate(contract("gts"), page),
                ValidationErrorCode.LIMIT_EXCEEDED,
                "listings[0].pokemon");
    }

    // The document limits run before the schema, so an oversized list is refused before its elements are looked at.
    @Test
    void checksDocumentLimitsBeforeTheSchema() throws Exception {
        final var items = String.join(",", Collections.nCopies(1001, "0"));
        final var page = """
                { "pageLabel": "1/5", "isFirstPage": true, "isLastPage": false, "listings": [%s] }
                """.formatted(items);

        assertFailure(validator.validate(contract("gts"), page), ValidationErrorCode.LIMIT_EXCEEDED, "listings");
    }

    @Test
    void rejectsADocumentThatIsNotAnObject() throws Exception {
        assertFailure(
                validator.validate(contract("text-number"), "[1, 2]"),
                ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH,
                "root");
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        assertFailure(
                validator.validate(contract("text-number"), "{\"count\": 1,"),
                ValidationErrorCode.PROPERTIES_SCHEMA_MISMATCH,
                "root");
    }

    static Contract contract(String fixture) throws IOException, ParseException {
        final var path = Path.of(System.getProperty("user.dir"), "schema", "fixtures", "valid", fixture + ".json");

        return new ContractParser().parse(Files.readString(path));
    }

    static void assertValid(ValidationResult result) {
        assertTrue(result.isValid(), () -> "Expected valid, got " + result.getError().getCode()
                + " at " + result.getError().getPath());
    }

    static void assertFailure(ValidationResult result, ValidationErrorCode code, String path) {
        assertFalse(result.isValid(), "Expected " + code + " at " + path + ", got valid");
        assertEquals(code, result.getError().getCode());
        assertEquals(path, result.getError().getPath());
    }
}
