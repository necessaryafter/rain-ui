package com.rainframework.ui.protocol.payload;

import com.rainframework.ui.protocol.ContractParser;
import com.rainframework.ui.protocol.ParseException;
import com.rainframework.ui.protocol.TypeSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadTest {
    private static final String FULL = """
            {
              "name": "Pikachu",
              "count": 3,
              "total": 9000000000,
              "ratio": 1.5,
              "enabled": true,
              "note": "urgente",
              "owner": { "name": "Ash" },
              "tags": ["fire", "rare"],
              "rows": [{ "id": "a1" }, { "id": "a2" }],
              "icon": "fire"
            }
            """;

    // The same payload with every optional field left out.
    private static final String MINIMAL = """
            {
              "name": "Pikachu",
              "count": 3,
              "total": 1,
              "ratio": 0,
              "enabled": false,
              "owner": { "name": "Ash" },
              "tags": [],
              "rows": [],
              "icon": "fire"
            }
            """;

    private static TypeSchema.ObjectType schema;

    @BeforeAll
    static void loadSchema() throws ParseException {
        final var contract = new ContractParser().parse("""
                {
                  "schemaVersion": 0,
                  "id": "test:payload",
                  "properties": {},
                  "actions": {
                    "test:all": {
                      "kind": "object",
                      "fields": {
                        "name": { "kind": "string" },
                        "count": { "kind": "int" },
                        "total": { "kind": "long" },
                        "ratio": { "kind": "double" },
                        "enabled": { "kind": "bool" },
                        "note": { "kind": "string", "optional": true },
                        "level": { "kind": "int", "optional": true },
                        "owner": { "kind": "object", "fields": { "name": { "kind": "string" } } },
                        "extra": { "kind": "object", "fields": { "name": { "kind": "string" } }, "optional": true },
                        "tags": { "kind": "list", "of": { "kind": "string" } },
                        "rows": {
                          "kind": "list",
                          "of": { "kind": "object", "fields": { "id": { "kind": "string" } } }
                        },
                        "icon": { "kind": "asset" }
                      }
                    }
                  },
                  "root": { "type": "text", "props": { "value": "x" }, "children": [] }
                }
                """);

        schema = (TypeSchema.ObjectType) contract.actions().get("test:all");
    }

    @Test
    void readsEachScalarWithItsAccessor() {
        final var payload = Payload.of(schema, FULL);

        assertEquals("Pikachu", payload.getString("name"));
        assertEquals(3, payload.getInt("count"));
        assertEquals(9_000_000_000L, payload.getLong("total"));
        assertEquals(1.5, payload.getDouble("ratio"));
        assertTrue(payload.getBool("enabled"));
        assertEquals("fire", payload.getString("icon"));
    }

    @Test
    void neverConvertsBetweenTypes() {
        final var payload = Payload.of(schema, FULL);

        assertThrows(PayloadTypeException.class, () -> payload.getInt("name"));
        assertThrows(PayloadTypeException.class, () -> payload.getString("count"));
        assertThrows(PayloadTypeException.class, () -> payload.getLong("count"));
        assertThrows(PayloadTypeException.class, () -> payload.getInt("total"));
        assertThrows(PayloadTypeException.class, () -> payload.getDouble("count"));
        assertThrows(PayloadTypeException.class, () -> payload.getBool("name"));
    }

    @Test
    void readsOptionalFieldsWithFind() {
        final var full = Payload.of(schema, FULL);
        final var minimal = Payload.of(schema, MINIMAL);

        assertEquals("urgente", full.findString("note"));
        assertNull(minimal.findString("note"));
        assertNull(minimal.findInt("level"));
        assertNull(minimal.findObject("extra"));
    }

    @Test
    void treatsAnExplicitNullAsAbsent() {
        final var json = MINIMAL.replace("\"icon\": \"fire\"", "\"icon\": \"fire\", \"note\": null");
        final var payload = Payload.of(schema, json);

        assertNull(payload.findString("note"));
    }

    @Test
    void failsWhenARequiredAccessorFindsNoValue() {
        final var minimal = Payload.of(schema, MINIMAL);

        assertThrows(PayloadTypeException.class, () -> minimal.getString("note"));
        assertThrows(PayloadTypeException.class, () -> minimal.getObject("extra"));
    }

    @Test
    void failsOnAnUndeclaredKey() {
        final var payload = Payload.of(schema, FULL);

        assertThrows(PayloadTypeException.class, () -> payload.getString("sellerId"));
        assertThrows(PayloadTypeException.class, () -> payload.findString("sellerId"));
    }

    @Test
    void readsNestedObjectsAndLists() {
        final var payload = Payload.of(schema, FULL);

        assertEquals("Ash", payload.getObject("owner").getString("name"));
        assertEquals(List.of("fire", "rare"), payload.getStringList("tags"));

        final var rows = payload.getObjectList("rows");
        assertEquals(2, rows.size());
        assertEquals("a2", rows.get(1).getString("id"));
    }

    @Test
    void readsEmptyLists() {
        final var payload = Payload.of(schema, MINIMAL);

        assertTrue(payload.getStringList("tags").isEmpty());
        assertTrue(payload.getObjectList("rows").isEmpty());
        assertFalse(payload.getBool("enabled"));
    }

    @Test
    void rejectsListAccessorsOfTheWrongKind() {
        final var payload = Payload.of(schema, FULL);

        assertThrows(PayloadTypeException.class, () -> payload.getStringList("rows"));
        assertThrows(PayloadTypeException.class, () -> payload.getObjectList("tags"));
        assertThrows(PayloadTypeException.class, () -> payload.getObject("tags"));
    }
}
