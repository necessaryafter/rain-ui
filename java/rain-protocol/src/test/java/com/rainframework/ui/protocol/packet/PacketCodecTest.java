package com.rainframework.ui.protocol.packet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PacketCodecTest {
    private static final String HASH = "ab38c5d70aaddd5a86f2cc907cd893a2fa76895a1e8b1363ad3635add776bbef";
    private static final String PROPERTIES = "{\"listings\":[{\"pokemonName\":\"Pokémon à venda\"}]}";

    @Test
    void roundTripsServerHello() throws PacketDecodeException {
        final var packet = new ServerHello(RainProtocol.VERSION, "https://cdn.example.com/rain/assets");

        assertEquals(packet, roundTrip(RainPackets.SERVER_HELLO, packet));
    }

    @Test
    void roundTripsClientHello() throws PacketDecodeException {
        final var packet = new ClientHello(RainProtocol.VERSION);

        assertEquals(packet, roundTrip(RainPackets.CLIENT_HELLO, packet));
    }

    @Test
    void roundTripsOpenScreen() throws PacketDecodeException {
        final var packet = new OpenScreen(7, "gts:shop/listings", HASH, 3, PROPERTIES);

        assertEquals(packet, roundTrip(RainPackets.OPEN_SCREEN, packet));
    }

    @Test
    void roundTripsUpdateScreen() throws PacketDecodeException {
        final var packet = new UpdateScreen(7, 4, PROPERTIES);

        assertEquals(packet, roundTrip(RainPackets.UPDATE_SCREEN, packet));
    }

    @Test
    void roundTripsInteractionRejected() throws PacketDecodeException {
        final var packet = new InteractionRejected(7, 4);

        assertEquals(packet, roundTrip(RainPackets.INTERACTION_REJECTED, packet));
    }

    @Test
    void roundTripsCloseScreen() throws PacketDecodeException {
        final var packet = new CloseScreen(7);

        assertEquals(packet, roundTrip(RainPackets.CLOSE_SCREEN, packet));
    }

    @Test
    void roundTripsInteract() throws PacketDecodeException {
        final var packet = new Interact(7, 4, "gts:open", "{\"listingId\":\"a1\"}");

        assertEquals(packet, roundTrip(RainPackets.INTERACT, packet));
    }

    @Test
    void roundTripsScreenClosed() throws PacketDecodeException {
        final var packet = new ScreenClosed(7);

        assertEquals(packet, roundTrip(RainPackets.SCREEN_CLOSED, packet));
    }

    @Test
    void roundTripsScreenFailed() throws PacketDecodeException {
        for (final var reason : ScreenFailureReason.values()) {
            final var packet = new ScreenFailed(7, reason);

            assertEquals(packet, roundTrip(RainPackets.SCREEN_FAILED, packet));
        }
    }

    @Test
    void encodesVarIntsLikeMinecraft() {
        assertArrayEquals(bytes(0x00), new PacketWriter().writeVarInt(0).toByteArray());
        assertArrayEquals(bytes(0x7f), new PacketWriter().writeVarInt(127).toByteArray());
        assertArrayEquals(bytes(0x80, 0x01), new PacketWriter().writeVarInt(128).toByteArray());
        assertArrayEquals(
                bytes(0xff, 0xff, 0xff, 0xff, 0x07),
                new PacketWriter().writeVarInt(Integer.MAX_VALUE).toByteArray());
    }

    @Test
    void decodesVarIntsLikeMinecraft() throws PacketDecodeException {
        assertEquals(0, new PacketReader(bytes(0x00)).readVarInt());
        assertEquals(128, new PacketReader(bytes(0x80, 0x01)).readVarInt());
        assertEquals(Integer.MAX_VALUE, new PacketReader(bytes(0xff, 0xff, 0xff, 0xff, 0x07)).readVarInt());
    }

    // Locks the wire format: instanceId, screenId, 32 raw hash bytes, revision, propertiesJson.
    @Test
    void encodesOpenScreenToTheExpectedBytes() {
        final var packet = new OpenScreen(1, "gts:listings", HASH, 2, "{}");

        final var expected = new ByteArrayOutputStream();
        expected.write(0x01);
        expected.write(12);
        expected.writeBytes("gts:listings".getBytes(StandardCharsets.UTF_8));
        expected.writeBytes(HexFormat.of().parseHex(HASH));
        expected.write(0x02);
        expected.write(2);
        expected.writeBytes("{}".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(expected.toByteArray(), RainPackets.OPEN_SCREEN.encode(packet));
    }

    @Test
    void removesTheTrailingSlashFromTheAssetBaseUrl() throws PacketDecodeException {
        final var bytes = new PacketWriterBytes().varInt(1).string("https://cdn.example.com/assets/").toArray();

        assertEquals("https://cdn.example.com/assets", RainPackets.SERVER_HELLO.decode(bytes).assetBaseUrl());
    }

    @Test
    void decodesAnUnknownFailureReasonAsOther() throws PacketDecodeException {
        final var bytes = new PacketWriterBytes().varInt(7).string("TIMEOUT_FROM_A_NEWER_CLIENT").toArray();

        assertEquals(new ScreenFailed(7, ScreenFailureReason.OTHER), RainPackets.SCREEN_FAILED.decode(bytes));
    }

    @Test
    void rejectsATruncatedPacket() {
        final var bytes = RainPackets.OPEN_SCREEN.encode(new OpenScreen(1, "gts:listings", HASH, 2, "{}"));
        final var truncated = Arrays.copyOf(bytes, bytes.length - 1);

        assertThrows(PacketDecodeException.class, () -> RainPackets.OPEN_SCREEN.decode(truncated));
    }

    @Test
    void rejectsTrailingBytes() {
        final var bytes = new PacketWriterBytes().varInt(7).varInt(0).toArray();

        assertThrows(PacketDecodeException.class, () -> RainPackets.CLOSE_SCREEN.decode(bytes));
    }

    @Test
    void rejectsAVarIntLongerThanFiveBytes() {
        assertThrows(PacketDecodeException.class, () -> RainPackets.CLOSE_SCREEN.decode(
                bytes(0x80, 0x80, 0x80, 0x80, 0x80, 0x01)));
    }

    @Test
    void rejectsAVarIntThatOverflowsIntoANegativeValue() {
        assertThrows(PacketDecodeException.class, () -> RainPackets.CLOSE_SCREEN.decode(
                bytes(0xff, 0xff, 0xff, 0xff, 0x0f)));
    }

    // A 2 GiB length prefix in a 10-byte packet must be refused from the prefix alone, not by trying to allocate it.
    @Test
    void rejectsADeclaredLengthOverTheFieldLimitWithoutAllocating() {
        final var properties = new PacketWriterBytes()
                .varInt(1)
                .varInt(1)
                .varInt(Integer.MAX_VALUE)
                .raw(0x7b)
                .toArray();
        final var actionId = new PacketWriterBytes()
                .varInt(1)
                .varInt(1)
                .varInt(Integer.MAX_VALUE)
                .raw(0x61)
                .toArray();

        assertThrows(PacketDecodeException.class, () -> RainPackets.UPDATE_SCREEN.decode(properties));
        assertThrows(PacketDecodeException.class, () -> RainPackets.INTERACT.decode(actionId));
    }

    @Test
    void rejectsAnActionIdOverTheLengthLimit() {
        final var actionId = "gts:" + "a".repeat(253);

        assertEquals(257, actionId.length());
        assertThrows(PacketDecodeException.class, () -> RainPackets.INTERACT.decode(interact(actionId, "{}")));
    }

    @Test
    void acceptsAnActionIdAtTheLengthLimit() throws PacketDecodeException {
        final var actionId = "gts:" + "a".repeat(252);

        assertEquals(actionId, RainPackets.INTERACT.decode(interact(actionId, "{}")).actionId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Buy", "a:B", ":x", "gts:", "gts:open now", "gts:../etc"})
    void rejectsAnActionIdOutsideTheIdentifierFormat(String actionId) {
        assertThrows(PacketDecodeException.class, () -> RainPackets.INTERACT.decode(interact(actionId, "{}")));
    }

    @Test
    void rejectsAHashShorterThan32Bytes() {
        final var bytes = new PacketWriterBytes()
                .varInt(1)
                .string("gts:listings")
                .raw(new byte[31])
                .toArray();

        assertThrows(PacketDecodeException.class, () -> RainPackets.OPEN_SCREEN.decode(bytes));
    }

    @Test
    void rejectsAPayloadOverEightKibibytes() {
        final var payload = "{\"note\":\"" + "x".repeat(8192 - 11) + "\"}";

        assertEquals(8192, payload.length());
        assertThrows(
                PacketDecodeException.class,
                () -> RainPackets.INTERACT.decode(interact("gts:open", payload + " ")));
    }

    @Test
    void acceptsAPayloadOfExactlyEightKibibytes() throws PacketDecodeException {
        final var payload = "{\"note\":\"" + "x".repeat(8192 - 11) + "\"}";

        assertEquals(payload, RainPackets.INTERACT.decode(interact("gts:open", payload)).payloadJson());
    }

    @Test
    void rejectsPropertiesOver256Kibibytes() {
        final var properties = " ".repeat(256 * 1024 + 1);
        final var bytes = new PacketWriterBytes().varInt(1).varInt(1).string(properties).toArray();

        assertThrows(PacketDecodeException.class, () -> RainPackets.UPDATE_SCREEN.decode(bytes));
    }

    @Test
    void rejectsInvalidUtf8() {
        final var bytes = new PacketWriterBytes().varInt(1).varInt(1).varInt(2).raw(0xc3, 0x28).toArray();

        assertThrows(PacketDecodeException.class, () -> RainPackets.UPDATE_SCREEN.decode(bytes));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://cdn.example.com/assets?token=abc",
            "https://cdn.example.com/assets#top",
            "https://user:secret@cdn.example.com/assets",
            "file:///home/player/.minecraft",
            "ftp://cdn.example.com/assets",
            "/assets",
            "cdn.example.com/assets",
    })
    void rejectsAnAssetBaseUrlOutsideTheAllowedForm(String url) {
        final var bytes = new PacketWriterBytes().varInt(1).string(url).toArray();

        assertThrows(PacketDecodeException.class, () -> RainPackets.SERVER_HELLO.decode(bytes));
    }

    @Test
    void rejectsAnAssetBaseUrlOver1024Bytes() {
        final var url = "https://cdn.example.com/" + "a".repeat(1024 - 23);
        final var bytes = new PacketWriterBytes().varInt(1).string(url).toArray();

        assertEquals(1025, url.length());
        assertThrows(PacketDecodeException.class, () -> RainPackets.SERVER_HELLO.decode(bytes));
    }

    @Test
    void acceptsAPlainHttpAssetBaseUrl() throws PacketDecodeException {
        final var bytes = new PacketWriterBytes().varInt(1).string("http://127.0.0.1:8080").toArray();

        assertEquals("http://127.0.0.1:8080", RainPackets.SERVER_HELLO.decode(bytes).assetBaseUrl());
    }

    @Test
    void rejectsAFailureReasonOver64Bytes() {
        final var bytes = new PacketWriterBytes().varInt(7).string("X".repeat(65)).toArray();

        assertThrows(PacketDecodeException.class, () -> RainPackets.SCREEN_FAILED.decode(bytes));
    }

    private static <T> T roundTrip(PacketType<T> type, T packet) throws PacketDecodeException {
        return type.decode(type.encode(packet));
    }

    private static byte[] interact(String actionId, String payload) {
        return new PacketWriterBytes().varInt(7).varInt(4).string(actionId).string(payload).toArray();
    }

    private static byte[] bytes(int... values) {
        final var bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }

        return bytes;
    }

    // Writes raw bytes independently of PacketWriter, so malformed packets can be built without the code under test.
    private static final class PacketWriterBytes {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        PacketWriterBytes varInt(int value) {
            var remaining = value;
            while ((remaining & ~0x7f) != 0) {
                out.write((remaining & 0x7f) | 0x80);
                remaining >>>= 7;
            }

            out.write(remaining);
            return this;
        }

        PacketWriterBytes string(String value) {
            final var utf8 = value.getBytes(StandardCharsets.UTF_8);

            varInt(utf8.length);
            out.writeBytes(utf8);
            return this;
        }

        PacketWriterBytes raw(int... values) {
            out.writeBytes(bytes(values));
            return this;
        }

        PacketWriterBytes raw(byte[] values) {
            out.writeBytes(values);
            return this;
        }

        byte[] toArray() {
            return out.toByteArray();
        }
    }
}
