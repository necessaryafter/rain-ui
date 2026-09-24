package com.rainframework.ui.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainframework.ui.client.layout.Measurer;
import com.rainframework.ui.protocol.Contract;
import com.rainframework.ui.protocol.ContractParser;
import com.rainframework.ui.protocol.validation.ContractValidator;
import com.rainframework.ui.protocol.validation.PropertiesValidator;

import java.nio.file.Files;
import java.nio.file.Path;

public final class TestContracts {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    // Every character is 6 px wide and a line is 9 px tall, like the default Minecraft font's digits.
    public static final Measurer MONOSPACE = new Measurer() {

        @Override
        public int textWidth(String text, String fontHash) {
            return text.length() * 6;
        }

        @Override
        public int lineHeight(String fontHash) {
            return 9;
        }
    };

    private TestContracts() {
    }

    public static Contract fixture(String name) throws Exception {
        final var path = Path.of(System.getProperty("user.dir"), "schema", "fixtures", "valid", name + ".json");
        return parse(Files.readString(path));
    }

    /** Parses and validates, so tests only ever feed the client what a real server could. */
    public static Contract parse(String json) throws Exception {
        final var contract = new ContractParser().parse(json);
        final var result = new ContractValidator().validate(contract);
        if (!result.isValid()) {
            throw new IllegalArgumentException(result.getError().getCode() + " at " + result.getError().getPath());
        }

        return contract;
    }

    public static JsonNode properties(Contract contract, String json) throws Exception {
        final var result = new PropertiesValidator().validate(contract, json);
        if (!result.isValid()) {
            throw new IllegalArgumentException(result.getError().getCode() + " at " + result.getError().getPath());
        }

        return MAPPER.readTree(json);
    }
}
