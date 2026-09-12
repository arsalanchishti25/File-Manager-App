package com.example.aggregator.mqtt;

import com.example.aggregator.model.UploadInstructions;
import com.google.gson.JsonArray;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MqttMessageHandlerCompletionTest {

    @Test
    void mapsInternalIndexesToOneBasedContractOrders() {
        List<UploadInstructions.FSTarget> targets = List.of(
                target("fs-1", 1),
                target("fs-2", 2),
                target("fs-3", 3),
                target("fs-4", 4));

        JsonArray chunks = MqttMessageHandler.buildCompletionChunks(
                targets, List.of("crc-1", "crc-2", "crc-3", "crc-4"));

        for (int i = 0; i < 4; i++) {
            assertEquals(i + 1, chunks.get(i).getAsJsonObject().get("chunkOrder").getAsInt());
            assertEquals("fs-" + (i + 1),
                    chunks.get(i).getAsJsonObject().get("fsId").getAsString());
            assertEquals(i + 1,
                    chunks.get(i).getAsJsonObject().get("volumeGroup").getAsInt());
            assertEquals("crc-" + (i + 1),
                    chunks.get(i).getAsJsonObject().get("crc32").getAsString());
        }
    }

    private static UploadInstructions.FSTarget target(String fsId, int volumeGroup) {
        UploadInstructions.FSTarget target = new UploadInstructions.FSTarget();
        target.setFsId(fsId);
        target.setVolumeGroup(volumeGroup);
        return target;
    }
}
